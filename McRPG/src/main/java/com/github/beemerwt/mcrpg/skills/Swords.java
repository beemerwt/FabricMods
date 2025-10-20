package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.callback.PlayerEvents;
import com.github.beemerwt.mcrpg.config.skills.SwordsConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class Swords {

    private static final Identifier LIMIT_BREAK_ID = Identifier.of("mcrpg", "limit_break");
    private static final Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier>
            LIMIT_BREAK_REMOVE = ImmutableMultimap.of(
            EntityAttributes.ATTACK_DAMAGE,
            new EntityAttributeModifier(LIMIT_BREAK_ID, 0.0, EntityAttributeModifier.Operation.ADD_VALUE)
    );

    public static void register() {
        PlayerEvents.CHANGE_SLOT.register(Swords::onChangeSlot);
    }

    private static void onChangeSlot(
            ServerPlayerEntity player, EquipmentSlot slot, ItemStack oldStack, ItemStack newStack
    ) {
        McRPG.getLogger().debug("onChangeSlot: player={}, slot={}, oldStack={}, newStack={}",
                player.getStringifiedName(), slot, oldStack, newStack);

        if (slot == EquipmentSlot.MAINHAND && ItemClassifier.isSword(newStack.getItem()))
            Swords.applyLimitBreak(player, Leveling.getLevel(player, SkillType.SWORDS));
        else
            Swords.removeLimitBreak(player);
    }

    public static void applyLimitBreak(PlayerEntity player, int level) {
        SwordsConfig cfg = ConfigManager.getSkillConfig(SkillType.SWORDS);
        var bonusDamage = Leveling.getScaled(cfg.limitBreak.baseDamageBonus, cfg.limitBreak.maxDamageBonus, level);

        McRPG.getLogger().debug("Applying limit break: player={}, level={}, bonusDamage={}",
                player.getStringifiedName(), level, bonusDamage);

        var damageAttr = EntityAttributes.ATTACK_DAMAGE;
        var mod = new EntityAttributeModifier(
                LIMIT_BREAK_ID,
                bonusDamage,            // base is ~1.0, so + (mult-1) -> effective multiplier
                EntityAttributeModifier.Operation.ADD_VALUE
        );

        player.getAttributes().addTemporaryModifiers(ImmutableMultimap.of(damageAttr, mod));
    }

    public static void removeLimitBreak(PlayerEntity player) {
        // Remove limit break ability effects
        player.getAttributes().removeModifiers(LIMIT_BREAK_REMOVE);
    }
}
