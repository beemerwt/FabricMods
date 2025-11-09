package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.config.ability.SuperBreakerConfig;
import com.github.beemerwt.mcrpg.data.SuperAbilityRuntime;
import com.github.beemerwt.mcrpg.event.AttackBlockEvent;
import com.github.beemerwt.mcrpg.config.skills.MiningConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.util.EventBus;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.function.Supplier;

public class SuperBreaker extends SuperAbility<MiningConfig, SuperBreakerConfig> {
    public SuperBreaker(Supplier<SuperBreakerConfig> aConfigProvider) {
        super(SkillType.MINING, aConfigProvider);
        EventBus.on(AttackBlockEvent.class, this::onAttackBlock);
    }

    @Override protected String id() { return "mcrpg.ability.super_breaker"; }
    @Override protected String toolId() { return "mcrpg.tool.pickaxe"; }

    @Override
    protected boolean canReady(ServerPlayerEntity p) {
        return ItemClassifier.isPickaxe(p.getMainHandStack().getItem());
    }

    @Override
    boolean onActivate(AttackBlockEvent e) {
        if (super.onActivate(e)) {
            onAttackBlock(e);
            return true;
        }

        return false;
    }

    private void onAttackBlock(AttackBlockEvent attackBlockEvent) {
        var p = attackBlockEvent.player();
        if (runtime.getState(p) != SuperAbilityRuntime.State.ACTIVE) return;

        var hand = attackBlockEvent.hand();
        if (hand == Hand.OFF_HAND) return;

        var tool = p.getMainHandStack();
        if (!ItemClassifier.isPickaxe(tool.getItem())) return;

        var world = attackBlockEvent.world();
        var pos = attackBlockEvent.pos();
        var state = world.getBlockState(pos);

        // Only apply to pickaxes and mining-relevant blocks
        if (!tool.canMine(state, world, pos, p)) return;
        p.interactionManager.tryBreakBlock(pos);
    }
}
