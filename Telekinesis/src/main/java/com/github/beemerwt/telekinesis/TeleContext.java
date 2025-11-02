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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleContext {
    private static final Map<UUID, Long> FULL_NOTICE_COOLDOWN = new ConcurrentHashMap<>();
    private static final long FULL_NOTICE_MS = 10000L; // adjust to taste
    public static final String TK_BYPASS_TAG = "tk_bypass";

    public static final Logger LOGGER = LoggerFactory.getLogger("Telekinesis");
    public static final boolean DEBUG = false;
    public static final RegistryEntry.Reference<SoundEvent> NOTIFY_FULL_SOUND
            = SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;

    private static final ThreadLocal<UUID> DROP_GUARD = new ThreadLocal<>();

    // smart insertion that respects stack limits and merges to existing stacks
    // Only touch main inventory (0..35). Never armor (36..39).
    private static ItemStack insertIntoInventory(PlayerInventory inv, ItemStack original) {
        ItemStack remaining = original.copy();
        if (remaining.isEmpty()) return ItemStack.EMPTY;

        final int maxPerStack = inv.getMaxCountPerStack();
        // vanilla main section is 36 slots; clamp in case a mod alters size
        final int mainSlots = Math.min(36, inv.size());

        // 0) Top off OFFHAND first, but only if it already has a matching stack
        final int OFF = PlayerInventory.OFF_HAND_SLOT; // usually 40
        ItemStack offhand = inv.getStack(OFF);
        if (!remaining.isEmpty() && !offhand.isEmpty()
            && canCombine(offhand, remaining)) { // or ItemStack.canCombine(offhand, remaining)
            int offhandCap = Math.min(offhand.getMaxCount(), maxPerStack);
            int free = offhandCap - offhand.getCount();
            if (free > 0) {
                int move = Math.min(free, remaining.getCount());
                offhand.increment(move);
                remaining.decrement(move);
                inv.setStack(OFF, offhand);
            }
        }

        // 1) Merge into existing stacks in main inventory (0..35)
        for (int i = 0; i < mainSlots && !remaining.isEmpty(); i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) continue;
            if (!canCombine(slot, remaining)) continue; // or ItemStack.canCombine(slot, remaining)

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

    public static void insertOrDropNear(ServerPlayerEntity player, ItemStack stack, Vec3d pos) {
        if (stack.isEmpty() || player.isRemoved() || player.isDead()) return;

        ItemStack remaining = insertIntoInventory(player.getInventory(), stack);
        if (!remaining.isEmpty()) {
            ServerWorld world = player.getEntityWorld();
            ItemEntity ent = new ItemEntity(world, pos.x, pos.y, pos.z, remaining.copy());
            ent.addCommandTag(TK_BYPASS_TAG);
            world.spawnEntity(ent);
            maybeNotifyFull(player);
        }
    }

    public static ItemStack tryInsertToPlayer(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || player.isRemoved() || player.isDead()) return stack;
        return insertIntoInventory(player.getInventory(), stack);
    }

    public static void giveStacksOrDrop(ServerPlayerEntity player, Collection<ItemStack> stacks, Vec3d pos) {
        for (ItemStack st : stacks) insertOrDropNear(player, st, pos);
    }

    public static void creditXp(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return;
        if (!player.isRemoved() && !player.isDead()) player.addExperience(amount);
    }

    public static boolean vacuumTo(ItemEntity item, ServerPlayerEntity player) {
        if (item.isRemoved()) return false;
        final ItemStack stack = item.getStack();
        if (stack.isEmpty()) return false;
        if (player.isRemoved() || player.isDead()) return false;

        ItemStack remaining = insertIntoInventory(player.getInventory(), stack);
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
