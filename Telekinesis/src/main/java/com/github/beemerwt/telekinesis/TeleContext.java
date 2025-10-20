package com.github.beemerwt.telekinesis;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleContext implements AutoCloseable {
    private static final Map<UUID, Long> FULL_NOTICE_COOLDOWN = new ConcurrentHashMap<>();
    private static final long FULL_NOTICE_MS = 10000L; // adjust to taste

    private static final ThreadLocal<Deque<TeleContext>> TL = ThreadLocal.withInitial(ArrayDeque::new);
    public static final Logger LOGGER = LoggerFactory.getLogger("Telekinesis");
    public static final boolean DEBUG = false;
    public static final RegistryEntry.Reference<SoundEvent> NOTIFY_FULL_SOUND
            = SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;

    private static final ThreadLocal<UUID> DROP_GUARD = new ThreadLocal<>();

    // Tune to taste; this is only for the proximity fallback when there’s no active scope.
    private static final double FALLBACK_RADIUS = 6.5;

    public final ServerPlayerEntity player;
    public final Vec3d origin;
    public final double radiusSq;
    private boolean dirty = false;

    private TeleContext(ServerPlayerEntity p, Vec3d o, double r) {
        this.player = p;
        this.origin = o;
        this.radiusSq = r * r;
    }

    public static TeleContext push(ServerPlayerEntity p, Vec3d origin, double radius) {
        TeleContext c = new TeleContext(p, origin, radius);
        TL.get().push(c);
        if (DEBUG) LOGGER.info("[TK] push scope @ {}", origin);
        return c;
    }

    @Override
    public void close() {
        Deque<TeleContext> dq = TL.get();
        if (dq.isEmpty()) return;
        TeleContext top = dq.pop();
        if (DEBUG) LOGGER.info("[TK] pop scope");
        if (top.player != null && top.dirty) {
            top.player.playerScreenHandler.sendContentUpdates();
        }
    }

    public static boolean tryOwnerCapture(ItemEntity item) {
        if (item.isRemoved()) return false;
        final ItemStack stack = item.getStack();
        if (stack.isEmpty()) return false;

        final Entity owner = item.getOwner();
        if (!(owner instanceof ServerPlayerEntity player)) {
            return false; // no owner or not a server player
        }

        // Do not vacuum up if the owner is dead or disconnected
        if (player.isRemoved() || player.isDead()) {
            return false;
        }

        // Insert as much as possible into the owner's inventory
        ItemStack remaining = insertIntoInventory(player.getInventory(), stack);
        // NEW: mark dirty if any amount was inserted
        if (remaining.getCount() < stack.getCount()) {
            markScopeDirtyIfActive();
        }

        if (remaining.isEmpty()) {
            // fully captured -> despawn/cancel spawn
            return true;
        }

        // Could not fully insert: update the entity’s stack to the remainder, leave spawn alone
        item.setStack(remaining);

        // If we inserted zero (inventory completely full), show the feedback (cooldown gated)
        if (remaining.getCount() == stack.getCount()) {
            maybeNotifyFull(player);
        }

        return false;
    }

    /**
     * Radius-based capture (your legacy behavior), with a scope-first policy and a
     * proximity fallback. Returns true only if the ENTIRE stack was captured.
     */
    public static boolean tryCapture(ItemEntity item) {
        if (item.isRemoved()) return false;

        ItemStack stack = item.getStack();
        if (stack.isEmpty()) return false;

        // 1) Prefer the active Telekinesis scope owner (if any) and only if inside scope radius
        ServerPlayerEntity target = resolveScopedTarget(item);
        if (target == null) {
            // 2) Fallback: nearest non-spectator player within a small radius
            target = resolveNearestEligible(item, FALLBACK_RADIUS);
        }
        if (target == null) return false;

        // Insert as much as possible
        ItemStack remaining = insertIntoInventory(target.getInventory(), stack);

        // NEW: mark dirty if any amount was inserted
        if (remaining.getCount() < stack.getCount()) {
            markScopeDirtyIfActive();
        }

        if (remaining.isEmpty()) {
            // Fully captured -> no entity should spawn
            return true;
        }

        // Partial insert: leave remainder on the entity and let the spawn proceed
        item.setStack(remaining);

        // If nothing fit, notify once per cooldown; otherwise play a light pickup cue
        if (remaining.getCount() == stack.getCount()) {
            maybeNotifyFull(target);
        }

        return false;
    }

    // === NEW: smart insertion that respects stack limits and merges to existing stacks
    // Only touch main inventory (0..35). Never armor (36..39) or offhand (40).
    private static ItemStack insertIntoInventory(PlayerInventory inv, ItemStack original) {
        ItemStack remaining = original.copy();
        if (remaining.isEmpty()) return ItemStack.EMPTY;

        final int maxPerStack = inv.getMaxCountPerStack();
        // vanilla main section is 36 slots; clamp in case a mod alters size
        final int mainSlots = Math.min(36, inv.size());

        // 1) Merge into existing stacks in main inventory (0..35)
        for (int i = 0; i < mainSlots && !remaining.isEmpty(); i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) continue;
            if (!canCombine(slot, remaining)) continue;

            int slotCap = Math.min(slot.getMaxCount(), maxPerStack);
            int free = slotCap - slot.getCount();
            if (free <= 0) continue;

            int move = Math.min(free, remaining.getCount());
            if (move > 0) {
                slot.increment(move);
                remaining.decrement(move);
                inv.setStack(i, slot);
            }
        }

        // 2) Fill empty main slots
        for (int i = 0; i < mainSlots && !remaining.isEmpty(); i++) {
            if (!inv.getStack(i).isEmpty()) continue;

            int move = Math.min(remaining.getCount(), Math.min(remaining.getMaxCount(), maxPerStack));
            if (move <= 0) break;
            inv.setStack(i, remaining.split(move));
        }

        return remaining;
    }

    private static void maybeNotifyFull(ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUuid();
        Long last = FULL_NOTICE_COOLDOWN.get(id);
        if (last != null && (now - last) < FULL_NOTICE_MS) return;
        FULL_NOTICE_COOLDOWN.put(id, now);

        // Red "Inventory Full" as a subtitle. (Short title timing so it feels snappy.)
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.literal("Inventory Full").formatted(Formatting.RED)
        ));
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(
                5, 25, 10
        ));

        player.networkHandler.sendPacket(
                new PlaySoundS2CPacket(
                        NOTIFY_FULL_SOUND,
                        net.minecraft.sound.SoundCategory.PLAYERS,
                        player.getX(), player.getY(), player.getZ(),
                        0.6f, 0.8f, player.getRandom().nextLong()
                )
        );
    }

    // Partial insert into main (slots 0..35); returns leftover
    private static ItemStack insertIntoMainWithRemainder(PlayerInventory inv, ItemStack in) {
        if (in.isEmpty()) return ItemStack.EMPTY;

        ItemStack remaining = in.copy();
        final int maxPerStack = inv.getMaxCountPerStack();
        final int mainSlots = Math.min(36, inv.size());

        // merge pass
        for (int i = 0; i < mainSlots && !remaining.isEmpty(); i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(slot, remaining)) continue;

            int slotCap = Math.min(slot.getMaxCount(), maxPerStack);
            if (slot.getCount() >= slotCap) continue;

            int move = Math.min(remaining.getCount(), slotCap - slot.getCount());
            slot.increment(move);
            remaining.decrement(move);
            inv.setStack(i, slot);
        }

        // empty slot pass
        for (int i = 0; i < mainSlots && !remaining.isEmpty(); i++) {
            if (!inv.getStack(i).isEmpty()) continue;

            int move = Math.min(remaining.getCount(), Math.min(remaining.getMaxCount(), maxPerStack));
            inv.setStack(i, remaining.split(move));
        }

        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    /* =========================
   Target resolution helpers
   ========================= */

    /**
     * Try to send the item to the currently active “scope” player if:
     * - a scope is active, and
     * - the item position is within that scope’s radius.
     * NOTE: Wire these getters to your existing scope system.
     * If you already have different names/fields, just map them here.
     */
    private static ServerPlayerEntity resolveScopedTarget(ItemEntity item) {
        if (!hasActiveScope()) return null;

        ServerPlayerEntity owner = getScopeOwner();
        if (owner == null || owner.isRemoved() || owner.isDead()) return null;

        Vec3d center = getScopeCenter();
        double r2 = getScopeRadiusSq();
        if (center == null || r2 <= 0.0) return null;

        Vec3d pos = item.getEntityPos();
        if (pos.squaredDistanceTo(center) <= r2) {
            return owner;
        }
        return null;
    }

    /**
     * Find nearest eligible player within a radius. Excludes spectators/dead.
     */
    private static ServerPlayerEntity resolveNearestEligible(ItemEntity item, double radius) {
        if (!(item.getEntityWorld() instanceof ServerWorld world)) return null;
        Vec3d pos = item.getEntityPos();

        double r2 = radius * radius;
        ServerPlayerEntity best = null;
        double bestD2 = Double.POSITIVE_INFINITY;

        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isRemoved() || p.isDead() || p.isSpectator()) continue;

            double d2 = p.squaredDistanceTo(pos);
            if (d2 <= r2 && d2 < bestD2) {
                best = p;
                bestD2 = d2;
            }
        }
        return best;
    }

/* =========================
   Scope adapter placeholders
   =========================
   Replace these with your real scope plumbing. They’re here so this file
   compiles and you can quickly wire to whatever you already use (your
   push/pop scope you logged earlier: "[TK] push scope @ ...").
*/

    private static boolean hasActiveScope() {
        Deque<TeleContext> dq = TL.get();
        return dq != null && !dq.isEmpty();
    }

    private static ServerPlayerEntity getScopeOwner() {
        Deque<TeleContext> dq = TL.get();
        TeleContext top = (dq == null) ? null : dq.peek();
        return (top == null) ? null : top.player;
    }

    private static Vec3d getScopeCenter() {
        Deque<TeleContext> dq = TL.get();
        TeleContext top = (dq == null) ? null : dq.peek();
        return (top == null) ? null : top.origin;
    }

    private static double getScopeRadiusSq() {
        Deque<TeleContext> dq = TL.get();
        TeleContext top = (dq == null) ? null : dq.peek();
        return (top == null) ? 0.0 : top.radiusSq;
    }

    private static void markScopeDirtyIfActive() {
        Deque<TeleContext> dq = TL.get();
        TeleContext top = (dq == null) ? null : dq.peek();
        if (top != null) top.dirty = true;
    }

    // add inside TeleContext
    public static boolean vacuumTo(ItemEntity item, ServerPlayerEntity player) {
        if (item.isRemoved()) return false;
        final ItemStack stack = item.getStack();
        if (stack.isEmpty()) return false;
        if (player.isRemoved() || player.isDead()) return false;

        ItemStack remaining = insertIntoInventory(player.getInventory(), stack);
        // mark dirty if any inserted
        if (remaining.getCount() < stack.getCount()) {
            markScopeDirtyIfActive();
        }
        if (remaining.isEmpty()) {
            return true; // fully captured -> cancel spawn
        }
        item.setStack(remaining);
        if (remaining.getCount() == stack.getCount()) {
            maybeNotifyFull(player);
        }
        return false;
    }

    /* =========================
       Inventory helpers
       =========================
     */

    public static boolean canCombine(ItemStack a, ItemStack b) {
        if (!ItemStack.areItemsAndComponentsEqual(a, b)) return false;
        return a.getCount() < Math.min(a.getMaxCount(), b.getMaxCount());
    }

    public static void beginPlayerDrop(UUID playerId) { DROP_GUARD.set(playerId); }
    public static void endPlayerDrop() { DROP_GUARD.remove(); }

    // Returns true if this spawn happened inside ServerPlayerEntity.dropItem() for its owner
    public static boolean isGuardedPlayerDrop(ItemEntity item) {
        UUID guard = DROP_GUARD.get();
        if (guard == null) return false;
        Entity owner = item.getOwner();
        return owner instanceof ServerPlayerEntity sp && guard.equals(sp.getUuid());
    }
}
