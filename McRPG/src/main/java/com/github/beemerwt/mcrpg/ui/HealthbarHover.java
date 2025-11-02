package com.github.beemerwt.mcrpg.ui;

import com.github.beemerwt.mcrpg.text.NamedTextColor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HealthbarHover {
    static final class EntityInfo {
        final Text originalName;
        final RegistryKey<World> worldKey;
        int ticksRemaining;

        EntityInfo(Text originalName, RegistryKey<World> worldKey, int ticksRemaining) {
            this.originalName = originalName;
            this.worldKey = worldKey;
            this.ticksRemaining = ticksRemaining;
        }
    }


    // How long to keep the bar visible after last hover (ticks)
    private static final int SHOW_TICKS = 50;
    private static final double MAX_DISTANCE = 8.0;

    // Cache original names so we can restore
    private static final Map<UUID, EntityInfo> originalNames = new HashMap<>();

    // Heart glyphs via escapes to keep code ASCII-only
    private static final String HEART  = "❤";
    private static final int MAX_HEARTS = 10;
    private static final TextColor HEART_FULL_COLOR  = NamedTextColor.DARK_RED.asTextColor();
    private static final TextColor HEART_EMPTY_COLOR = NamedTextColor.DARK_GRAY.asTextColor();

    public static void init() {
        ServerTickEvents.START_SERVER_TICK.register(HealthbarHover::onTick);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            HealthbarHover.hideBar(entity); // restore original name & hide
            return true; // do not cancel death
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(HealthbarHover::onServerStop);
    }

    public static void onServerStop(MinecraftServer server) {
        for (var entry : originalNames.entrySet()) {
            UUID id = entry.getKey();
            EntityInfo info = entry.getValue();

            ServerWorld world = server.getWorld(info.worldKey);
            if (world == null) continue;

            var entity = world.getEntity(id);
            if (entity instanceof LivingEntity le) {
                restoreOnly(le, info);
            }
        }

        originalNames.clear(); // ensure full cleanup
    }

    private static void onTick(MinecraftServer server) {
        // 1) Refresh bars for players' look targets (this may add/refresh entries)
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            LivingEntity target = getLookEntity(p, MAX_DISTANCE);
            if (target != null) {
                showBar(target);
            }
        }

        // 2) Age out entries (NO direct map.remove calls inside helpers)
        var it = originalNames.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID id = e.getKey();
            EntityInfo info = e.getValue();

            ServerWorld world = server.getWorld(info.worldKey);
            if (world == null) {
                it.remove();           // world gone, just forget it
                continue;
            }

            var ent = world.getEntity(id);
            if (!(ent instanceof LivingEntity le) || !le.isAlive() || ent.isRemoved()) {
                // Can't restore safely; drop the record.
                it.remove();
                continue;
            }

            if (--info.ticksRemaining <= 0) {
                // Restore name/visibility but DO NOT touch the map here
                restoreOnly(le, info);
                it.remove();           // iterator performs the only structural remove
            }
        }
    }

    private static void showBar(LivingEntity le) {
        UUID id = le.getUuid();

        float hp  = Math.max(0f, le.getHealth());
        float max = Math.max(1f, le.getMaxHealth());

        // Compute how many "visible hearts" we should render (capped at 10)
        int heartsDisplayed = Math.min(MAX_HEARTS, (int) Math.ceil(max / 2.0));

        // Scale so that 10 hearts represents full HP if max > 20
        float hpPerHeart = max / heartsDisplayed;
        float filledHeartsExact = hp / hpPerHeart;
        int filledHearts = (int) Math.ceil(filledHeartsExact);

        if (filledHearts < 0) filledHearts = 0;
        if (filledHearts > heartsDisplayed) filledHearts = heartsDisplayed;

        var bar = Text.empty();
        for (int i = 0; i < heartsDisplayed; i++) {
            boolean isFull = i < filledHearts;
            bar = bar.append(
                    Text.literal(HEART)
                            .styled(s -> s.withColor(isFull ? HEART_FULL_COLOR : HEART_EMPTY_COLOR))
            );
        }

        EntityInfo existing = originalNames.get(id);
        if (existing == null) {
            // First time we've shown a bar for this entity: capture original
            EntityInfo first = new EntityInfo(
                le.getCustomName(),                          // original name (may be null)
                le.getEntityWorld().getRegistryKey(),        // current world
                SHOW_TICKS                                   // TTL
            );
            originalNames.put(id, first);
        } else {
            // Refresh TTL and world; since fields are final, replace the record but
            // preserve the *original* name we captured the first time.
            EntityInfo refreshed = new EntityInfo(
                existing.originalName,
                le.getEntityWorld().getRegistryKey(),
                SHOW_TICKS
            );
            originalNames.put(id, refreshed);
        }

        // Apply the bar
        le.setCustomName(bar);
        le.setCustomNameVisible(true);
    }

    private static void hideBar(LivingEntity le) {
        UUID id = le.getUuid();
        var info = originalNames.remove(id);
        if (info == null) return; // No cached name; nothing to do

        Text current = le.getCustomName();

        // Heuristic: only restore if the current name was our hearts bar (contains HEART),
        // or if the name is null (some mods clear names); otherwise, don't stomp on changes.
        if (current == null || current.getString().contains(HEART)) {
            le.setCustomName(info.originalName);   // may be null (that's fine)
            le.setCustomNameVisible(false);        // hide after our bar disappears
        }
    }

    // --- restore without altering the map (safe during iteration) ---
    private static void restoreOnly(LivingEntity le, EntityInfo info) {
        if (info == null) return;

        Text current = le.getCustomName();
        boolean looksLikeOurBar = current == null || current.getString().contains(HEART);

        if (looksLikeOurBar) {
            le.setCustomName(info.originalName);   // may be null
            le.setCustomNameVisible(false);
        }
    }

    // --- external helper: restore AND drop the cache entry immediately ---
    private static void clearBarNow(LivingEntity le) {
        EntityInfo info = originalNames.remove(le.getUuid());
        if (info != null) restoreOnly(le, info);
    }

    // Show only for hostile mobs; exclude tamed wolves explicitly.
    private static boolean shouldShowFor(LivingEntity e, ServerPlayerEntity viewer) {
        if (e instanceof WolfEntity w) {
            // Never show for tamed wolves
            if (w.isTamed()) return false;
            // Optional: show for untamed wolves only if they’re angry at the viewer
            // If you prefer showing untamed wolves always, just: return true;
            try {
                var angry = w.getAngryAt();
                if (angry == null) return false;
                return angry.equals(viewer.getUuid());
            } catch (Throwable t) {
                // Fallback if mappings change: show only if targeting the viewer
                return w.getTarget() == viewer;
            }
        }
        return e instanceof HostileEntity;
    }

    private static LivingEntity getLookEntity(ServerPlayerEntity p, double maxDist) {
        var start = p.getCameraPosVec(1.0f);
        var end   = start.add(p.getRotationVec(1.0f).multiply(maxDist));
        var blockHit = p.getEntityWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));

        double limit = (blockHit.getType() == HitResult.Type.BLOCK)
                ? blockHit.getPos().distanceTo(start)
                : maxDist;

        var dir = p.getRotationVec(1.0f);
        var box = p.getBoundingBox().stretch(dir.multiply(limit)).expand(1.0, 1.0, 1.0);

        LivingEntity best = null;
        double bestDist = limit + 1.0;

        for (var e : p.getEntityWorld().getOtherEntities(
                p, box,
                ent -> ent instanceof LivingEntity le && le.isAlive() && le.isAttackable() && shouldShowFor(le, p))) {

            var aabb = e.getBoundingBox().expand(0.3);
            var res = aabb.raycast(start, end);
            if (res.isPresent()) {
                double d = res.get().distanceTo(start);
                if (d < bestDist) {
                    best = (LivingEntity) e;
                    bestDist = d;
                }
            }
        }
        return best;
    }
}

