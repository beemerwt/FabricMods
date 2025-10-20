package com.github.beemerwt.mcrpg.ui;

import com.github.beemerwt.mcrpg.text.NamedTextColor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HealthbarHover {
    // How long to keep the bar visible after last hover (ticks)
    private static final int SHOW_TICKS = 12;
    private static final double MAX_DISTANCE = 8.0;

    // Track when we last showed a bar per-entity so we can hide it later
    private static final Map<UUID, Integer> entityExpiry = new HashMap<>();
    // Cache original names so we can restore
    private static final Map<UUID, Text> originalNames = new HashMap<>();

    // Heart glyphs via escapes to keep code ASCII-only
    private static final String HEART  = "❤";
    private static final int MAX_HEARTS = 10;
    private static final TextColor HEART_FULL_COLOR  = NamedTextColor.DARK_RED.asTextColor();
    private static final TextColor HEART_EMPTY_COLOR = NamedTextColor.DARK_GRAY.asTextColor();

    public static void init() {
        ServerTickEvents.START_SERVER_TICK.register(HealthbarHover::onTick);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            HealthbarHover.clearBarNow(entity); // restore original name & hide
            return true; // do not cancel death
        });
    }

    private static void onTick(MinecraftServer server) {
        // 1) Update for players looking at something
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            var target = getLookEntity(p, MAX_DISTANCE);
            if (target != null) {
                showBar(target);
            }
        }

        // 2) Hide expired bars
        var it = entityExpiry.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int remaining = e.getValue() - 1;
            if (remaining <= 0) {
                var id = e.getKey();
                var entity = server.getOverworld().getEntity(id); // good enough if most are in OW; for multi-dim, track Ref<ServerWorld>
                if (entity instanceof LivingEntity le) {
                    hideBar(le);
                }
                it.remove();
            } else {
                e.setValue(remaining);
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

        // Cache and update custom name
        originalNames.putIfAbsent(id, le.getCustomName());
        le.setCustomName(bar);
        le.setCustomNameVisible(true);

        // Refresh visibility timer
        entityExpiry.put(id, SHOW_TICKS);
    }

    private static void hideBar(LivingEntity le) {
        UUID id = le.getUuid();
        Text orig = originalNames.remove(id);
        le.setCustomName(orig); // may be null
        le.setCustomNameVisible(false);
    }

    // Used for lethal or forced cleanup; also clears caches
    private static void clearBarNow(LivingEntity le) {
        UUID id = le.getUuid();
        if (originalNames.containsKey(id)) {
            if (originalNames.get(id).getString().contains(HEART))
                le.setCustomName(null);
            else
                le.setCustomName(originalNames.get(id));

            le.setCustomNameVisible(false);
        }

        originalNames.remove(id);
        entityExpiry.remove(id);
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

