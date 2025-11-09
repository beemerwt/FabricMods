package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.event.AfterDamageEvent;
import com.github.beemerwt.mcrpg.event.AllowDamageEvent;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.AcrobaticsConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.service.XpModifier;
import com.github.beemerwt.mcrpg.util.EventBus;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

import java.util.Set;

public class Acrobatics extends Skill<AcrobaticsConfig> {

    private final static ThreadLocal<Integer> FALL_GUARD = ThreadLocal.withInitial(() -> 0);

    public Acrobatics() {
        super(SkillType.ACROBATICS);
        EventBus.intercept(AllowDamageEvent.class, this::onAllowDamage);
    }

    private ActionResult onAllowDamage(AllowDamageEvent e) {
        var damageSource = e.damageSource();
        var amount = e.amount();
        var player = e.player();

        if (!damageSource.isOf(DamageTypes.FALL)) return ActionResult.PASS;
        if (FALL_GUARD.get() > 0) return ActionResult.PASS; // Prevent recursion

        // TODO: Dodge

        float newAmount = Acrobatics.onFallDamage(player, amount);
        if (amount == newAmount) return ActionResult.PASS;

        FALL_GUARD.set(FALL_GUARD.get() + 1);
        try {
            McRPG.getLogger().debug("Acrobatics: Intercepting fall damage of {} for {} damage.",
                player.getStringifiedName(), amount);
            player.damage(player.getEntityWorld(), damageSource, newAmount);
            return ActionResult.FAIL; // Cancel original damage
        } finally {
            FALL_GUARD.set(FALL_GUARD.get() - 1);
        }
    }

    /**
     * Called when a player takes fall damage. Awards acrobatics XP based on the damage taken
     * @param player The player taking fall damage
     * @param damageTaken The raw damage taken before mitigations
     * @return The final damage taken after mitigations
     */
    public static float onFallDamage(ServerPlayerEntity player, float damageTaken) {
        AcrobaticsConfig cfg = ConfigManager.getSkillConfig(SkillType.ACROBATICS);
        var xp = cfg.fallXp * damageTaken;
        final float originalDamage = damageTaken;

        if (cfg.roll.enabled) {
            int level = Leveling.getLevel(player, SkillType.ACROBATICS);
            float chance = Leveling.getScaledPercentage(cfg.roll.baseChance, cfg.roll.maxChance, level);
            if (player.getRandom().nextFloat() < chance) {
                McRPG.getLogger().debug("Acrobatics: {} avoided fall damage (chance {}).",
                    player.getStringifiedName(), chance);

                float damage = Leveling.getScaled(cfg.roll.baseDamageReduction, cfg.roll.maxDamageReduction, level);
                damageTaken = Math.max(0, damageTaken - damage);
                xp += cfg.roll.xpAwarded;
            }
        }

        // Feather Falling enchantment bonus
        Set<RegistryEntry<Enchantment>> enchantments = player.getEquippedStack(EquipmentSlot.FEET)
            .getEnchantments().getEnchantments();
        if (enchantments != null && enchantments.stream().anyMatch(e -> e.matches(
            p -> p == Enchantments.FEATHER_FALLING)))
            xp *= (float) cfg.featherFallMultiplier;

        // Apply XP modifier from config
        var gained = (int)(Math.floor(xp * cfg.xpModifier));
        McRPG.getLogger().debug("Awarding {} acrobatics XP to {} for {} fall damage.", gained,
            player.getStringifiedName(), originalDamage);
        XpModifier.addFlat(player, SkillType.ACROBATICS, gained);
        return damageTaken;
    }
}
