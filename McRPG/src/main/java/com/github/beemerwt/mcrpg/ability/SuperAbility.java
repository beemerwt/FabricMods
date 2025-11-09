package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.config.SuperAbilityConfig;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.data.SuperAbilityRuntime;
import com.github.beemerwt.mcrpg.event.AttackBlockEvent;
import com.github.beemerwt.mcrpg.event.UseBlockEvent;
import com.github.beemerwt.mcrpg.event.UseItemEvent;
import com.github.beemerwt.mcrpg.managers.AbilityManager;
import com.github.beemerwt.mcrpg.util.EventBus;
import com.github.beemerwt.mcrpg.util.Messenger;
import com.github.beemerwt.mcrpg.util.SoundUtil;
import com.github.beemerwt.mcrpg.util.TickClock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.function.Supplier;

public non-sealed abstract class SuperAbility<SConfig extends SkillConfig, AConfig extends SuperAbilityConfig>
    extends Ability<SConfig, AConfig>
{
    protected final SuperAbilityRuntime runtime;

    public SuperAbility(SkillType skillType, Supplier<AConfig> aConfigSupplier) {
        super(skillType, aConfigSupplier);
        this.runtime = AbilityManager.getAbilityRuntime(this);

        EventBus.subscribe(UseItemEvent.class, e -> onReady(e.player(), e.hand()));
        EventBus.subscribe(UseBlockEvent.class, e -> onReady(e.player(), e.hand()));
        EventBus.subscribe(AttackBlockEvent.class, this::onActivate);
    }

    protected abstract String toolId();

    protected abstract boolean canReady(ServerPlayerEntity p);

    // lifecycle is managed by RuntimeManager; ability provides hooks
    protected ActionResult onReady(ServerPlayerEntity p, Hand hand) {
        if (p.isCreative() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!config.enabled || !canReady(p)) return ActionResult.PASS;
        if (runtime.getState(p) != SuperAbilityRuntime.State.IDLE) return ActionResult.PASS;

        Messenger.actionBar(p, Text.translatable("mcrpg.ability.ready", getColorizedTool()));
        runtime.tryReady(p);
        return ActionResult.SUCCESS;
    }

    protected void onReadyExpired(ServerPlayerEntity p) {
        Messenger.actionBar(p, Text.translatable("mcrpg.ability.ready_expire", getColorizedTool()));
    }

    boolean onActivate(AttackBlockEvent e) {
        if (!config.enabled) return false;

        var p = e.player();
        var state = runtime.getState(p);
        if (state != SuperAbilityRuntime.State.READY) return false;

        int level = Leveling.getLevel(p, SkillType.MINING);
        long duration = Leveling.getScaledTicks(config.baseDuration, config.maxDuration, level);
        long cooldown = Leveling.getScaledTicks(config.baseCooldown, config.minCooldown, level);

        if (!runtime.tryActivate(p, duration, cooldown))
            return false;

        Messenger.actionBar(p, Text.translatable("mcrpg.ability.activated", getColorizedName()));
        SoundUtil.playSound(p, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), 1.0f, 1.0f);

        McRPG.getLogger().debug("{} activated {} for {} ticks (ticksnow {})",
            p.getStringifiedName(), id(), getDuration(p), TickClock.now());
        return true;
    }

    protected long getDuration(ServerPlayerEntity p) {
        int level = Leveling.getLevel(p, skillType);
        return Leveling.getScaledTicks(config.baseDuration, config.maxDuration, level);
    }

    void onExpire(ServerPlayerEntity p) {
        Messenger.actionBar(p, Text.translatable("mcrpg.ability.expired", getColorizedName()));
        SoundUtil.playSound(p, SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);

        McRPG.getLogger().debug("{}'s {} expired (ticksnow {})", p.getStringifiedName(), id(), TickClock.now());
    }

    @Override
    public void tick(MinecraftServer server) {
        runtime.tick();

        for (var t : runtime.drainTransitions()) {
            var player = server.getPlayerManager().getPlayer(t.playerId);
            if (player == null) continue;

            switch (t.from) {
                case READY -> {
                    if (t.to == SuperAbilityRuntime.State.IDLE)
                        onReadyExpired(player);
                }
                case ACTIVE -> {
                    if (t.to == SuperAbilityRuntime.State.COOLDOWN)
                        onExpire(player);
                }
            }
        }
    }

    private Text getColorizedName() {
        var color = Formatting.byName(skillConfig.bossbarColor);
        return Text.translatable(id()).formatted(color);
    }

    private Text getColorizedTool() {
        var color = Formatting.byName(skillConfig.bossbarColor);
        return Text.translatable(toolId()).formatted(color);
    }
}
