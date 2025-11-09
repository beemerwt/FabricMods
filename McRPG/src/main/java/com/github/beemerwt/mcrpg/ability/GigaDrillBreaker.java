package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.config.ability.GigaDrillBreakerConfig;
import com.github.beemerwt.mcrpg.data.SuperAbilityRuntime;
import com.github.beemerwt.mcrpg.event.AttackBlockEvent;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.ExcavationConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.util.EventBus;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public class GigaDrillBreaker extends SuperAbility<ExcavationConfig, GigaDrillBreakerConfig> {
    public GigaDrillBreaker(Supplier<GigaDrillBreakerConfig> aConfigSupplier) {
        super(SkillType.EXCAVATION, aConfigSupplier);
        EventBus.on(AttackBlockEvent.class, this::onAttackBlock);
    }

    private void onAttackBlock(AttackBlockEvent e) {
        var p = e.player();
        if (runtime.getState(p) != SuperAbilityRuntime.State.ACTIVE) return;

        var hand = e.hand();
        if (hand != Hand.MAIN_HAND) return;

        var tool = p.getMainHandStack();
        if (!ItemClassifier.isShovel(tool.getItem())) return;

        var world = e.world();
        var pos = e.pos();
        var state = world.getBlockState(pos);

        // Only apply to shovels and excavation-relevant blocks
        if (!tool.canMine(state, world, pos, p)) return;
        p.interactionManager.tryBreakBlock(pos);
    }

    @Override protected String toolId() { return "mcrpg.tool.shovel"; }
    @Override protected String id() { return "mcrpg.ability.giga_drill_breaker"; }

    @Override
    protected boolean canReady(ServerPlayerEntity p) {
        return ItemClassifier.isShovel(p.getMainHandStack().getItem());
    }

    @Override
    boolean onActivate(AttackBlockEvent e) {
        if (super.onActivate(e)) {
            onAttackBlock(e); // force break on activation hit
            return true;
        }

        return false;
    }
}
