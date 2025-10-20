package com.github.beemerwt.mcrpg.abilities;

import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.MiningConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SuperBreaker {

    private static final Identifier SUPER_BREAKER_ID = Identifier.of("mcrpg", "super_breaker");
    private static final Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier>
            SUPER_BREAKER_REMOVE = ImmutableMultimap.of(
            EntityAttributes.BLOCK_BREAK_SPEED,
            new EntityAttributeModifier(SUPER_BREAKER_ID, 0.0, EntityAttributeModifier.Operation.ADD_VALUE)
    );

    public static void activateFor(ServerPlayerEntity player, int level) {
        MiningConfig cfg = ConfigManager.getSkillConfig(SkillType.MINING);
        double multiplier = Leveling.getScaled(cfg.superBreaker.baseSpeedMultipier,
                cfg.superBreaker.maxSpeedMultipier, level);
        if (multiplier < 1.0) multiplier = 1.0; // safety

        var speedAttr = EntityAttributes.BLOCK_BREAK_SPEED;
        var mod = new EntityAttributeModifier(
                SUPER_BREAKER_ID,
                multiplier - 1.0,            // base is ~1.0, so + (mult-1) -> effective multiplier
                EntityAttributeModifier.Operation.ADD_VALUE
        );

        player.getAttributes().addTemporaryModifiers(ImmutableMultimap.of(speedAttr, mod));
    }

    public static void deactivateFor(ServerPlayerEntity player) {
        player.getAttributes().removeModifiers(SUPER_BREAKER_REMOVE);
    }
}
