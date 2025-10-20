package com.github.beemerwt.mcrpg.events;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.GeneralConfig;
import com.github.beemerwt.mcrpg.mixin.access.DamageTrackerAccessor;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatEvents {
    private static final Map<UUID, Object2IntOpenHashMap<UUID>> CURSORS = new HashMap<>();

    private CombatEvents() {}

    enum EntityState {
        ALIVE,
        JUST_DIED,
        UNKNOWN
    };

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victimEntity, src, amount, v1, v2) -> {
            if (victimEntity instanceof LivingEntity victim) {
                processNewDamageRecords(victim);
            }
        });

        // Ensure we also pay the killing blow BEFORE vanilla logs/clears
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof LivingEntity victim) {
                processNewDamageRecords(victim);
            }
            return true; // do not cancel death
        });

        // Cleanup after death
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, src) -> {
            if (entity != null) CURSORS.remove(entity.getUuid());
        });
    }

    private static void processNewDamageRecords(LivingEntity victim) {
        var tracker = victim.getDamageTracker();
        var records = ((DamageTrackerAccessor) tracker).getRecentDamage();
        if (records == null || records.isEmpty()) return;

        var perAttacker = CURSORS.computeIfAbsent(victim.getUuid(), k -> {
            var m = new Object2IntOpenHashMap<UUID>();
            m.defaultReturnValue(-1);
            return m;
        });

        for (int i = 0; i < records.size(); i++) {
            var rec = records.get(i);
            var src = rec.damageSource();

            var attacker = resolveAttacker(src);
            if (!(attacker instanceof ServerPlayerEntity sp)) continue;

            int last = perAttacker.getInt(sp.getUuid());
            if (i <= last) continue; // already paid

            float applied = rec.damage(); // post-mitigation, applied to HP
            if (applied <= 0f) {
                perAttacker.put(sp.getUuid(), i);
                continue;
            }

            var skill = classifyFromContext(sp, src);
            if (skill != null) {
                var cfg  = ConfigManager.getSkillConfig(skill);
                long base = baseKillXp(victim, cfg.xpModifier);
                long xp   = Math.round(applied * base);
                if (xp < 1) xp = 1;

                Leveling.addXp(sp, skill, xp);

                // Debug
                var id = Registries.ENTITY_TYPE.getId(victim.getType());
                McRPG.getLogger().debug(
                        "XP {} -> {} for {} dmg to {} (recIdx={})",
                        xp, sp.getGameProfile().name(), applied, id, i
                );
            }

            perAttacker.put(sp.getUuid(), i);
        }
    }

    // ------------ Classification & XP helpers ------------
    private static SkillType classifyMelee(ItemStack mainHand) {
        if (mainHand.isEmpty()) return SkillType.UNARMED;
        if (ItemClassifier.isSword(mainHand.getItem())) return SkillType.SWORDS;
        if (ItemClassifier.isAxe(mainHand.getItem())) return SkillType.AXES;
        if (ItemClassifier.isBowLike(mainHand.getItem())) return null; // not melee

        if (ItemClassifier.isTrident(mainHand.getItem())) {
            McRPG.getLogger().debug("Melee hit not counted with trident: {}", mainHand.getItem());
            return null; // trident, not melee
        }

        if (ItemClassifier.isMace(mainHand.getItem())) {
            McRPG.getLogger().debug("Melee hit not counted with mace: {}", mainHand.getItem());
            return null; // mace, not melee
        }

        if (ItemClassifier.isTool(mainHand.getItem())) {
            McRPG.getLogger().debug("Melee hit not counted with tool: {}", mainHand.getItem());
            return null; // other tool, not melee
        }

        return SkillType.UNARMED;
    }

    // Distinguish archery vs melee on kill using DamageSource.
    private static SkillType classifyFromContext(ServerPlayerEntity sp, DamageSource source) {
        Entity src = source.getSource();

        // Otherwise, melee classification
        return classifyMelee(sp.getMainHandStack());

        /*
        if (src instanceof PersistentProjectileEntity proj) {
            // If the projectile was shot by this player, count as ARCHERY
            if (proj.getOwner() instanceof ServerPlayerEntity owner && owner.getUuid().equals(sp.getUuid()))
                return SkillType.ARCHERY;
        }*/
    }

    // Very light scaling by mob type/hp
    private static long baseKillXp(LivingEntity victim, double modifier) {
        GeneralConfig cfg = ConfigManager.getGeneralConfig();
        var id = Registries.ENTITY_TYPE.getId(victim.getType());
        var baseXp = cfg.mobXpModifiers.getOrDefault(id.toString(), 1.0);
        if (baseXp <= 0.0) baseXp = 1.0; // sanity clamp
        baseXp *= 10;

        long result = Math.round(baseXp * modifier);
        McRPG.getLogger().debug("Base XP for killing {} is {}", id, result);
        return result;
    }

    // Try to pull the player responsible for the damage
    private static Entity resolveAttacker(DamageSource source) {
        // source.getAttacker() is usually the player for melee.
        // source.getSource() may be the projectile for ranged weapons.
        Entity a = source.getAttacker();
        if (a != null) return a;
        return source.getSource();
    }
}

