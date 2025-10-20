package com.github.beemerwt.mcrpg.events;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.data.ActiveAbilityType;
import com.github.beemerwt.mcrpg.managers.AbilityManager;
import com.github.beemerwt.mcrpg.skills.Repair;
import com.github.beemerwt.mcrpg.skills.Salvage;
import com.github.beemerwt.mcrpg.skills.Woodcutting;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.impl.event.interaction.InteractionEventsRouter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

// TODO: Merge with AbilityManager to handle the intermediate events and emit callbacks
// TODO: Then register the callbacks in the individual skill classes

public class AbilityEvents {
    public static void register() {
        UseBlockCallback.EVENT.register(AbilityEvents::onPlayerUse);
        UseItemCallback.EVENT.register((player, world, hand) ->
                onPlayerUse(player, world, hand, null));

        AttackBlockCallback.EVENT.register(AbilityEvents::onStartBreak);
    }

    private static ActionResult onPlayerUse(PlayerEntity player,
                                           World world,
                                           Hand hand,
                                           @Nullable BlockHitResult hitResult)
    {
        if (world.isClient()) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        if (hand == Hand.OFF_HAND) return ActionResult.PASS;
        if (!player.isSneaking()) return ActionResult.PASS;
        return readyAbility(sp);
    }

    private static ActionResult readyAbility(ServerPlayerEntity sp) {
        McRPG.getLogger().debug("AbilityEvents: onPlayerUse: Player {}",
                sp.getName().getString());

        var handItem = sp.getMainHandStack().getItem();

        if (ItemClassifier.isShovel(handItem) && AbilityManager.canActivate(sp, ActiveAbilityType.GIGA_DRILL_BREAKER))
        {
            AbilityManager.readyAbility(sp, ActiveAbilityType.GIGA_DRILL_BREAKER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isPickaxe(handItem) && AbilityManager.canActivate(sp, ActiveAbilityType.SUPER_BREAKER))
        {
            AbilityManager.readyAbility(sp, ActiveAbilityType.SUPER_BREAKER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isAxe(handItem) && AbilityManager.canActivate(sp, ActiveAbilityType.TREE_FELLER))
        {
            AbilityManager.readyAbility(sp, ActiveAbilityType.TREE_FELLER);
            return ActionResult.SUCCESS;
        }

        if (ItemClassifier.isHoe(handItem) && AbilityManager.canActivate(sp, ActiveAbilityType.GREEN_TERRA))
        {
            AbilityManager.readyAbility(sp, ActiveAbilityType.GREEN_TERRA);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    public static ActionResult onStartBreak(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        if (player == null || player.isSpectator() || player.isCreative()) return ActionResult.PASS;
        if (world.isClient()) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

        handleReadiedAbility(player, sp, sw, pos);
        Woodcutting.onStartBreak(sp, sw, pos);
        return ActionResult.PASS;
    }

    private static void handleReadiedAbility(PlayerEntity player, ServerPlayerEntity sp,
                                                     ServerWorld sw, BlockPos pos)
    {
        var readied = AbilityManager.getReadiedAbility(sp);
        if (readied.isEmpty()) return;
        McRPG.getLogger().debug("AbilityEvents: onStartBreak: Player {} has readied ability {}",
                player.getName().getString(), readied.get().getDisplayName());

        var state = sw.getBlockState(pos);
        if (state == null) return;

        var block = state.getBlock();
        if (block == null) return;

        var blockName = Registries.BLOCK.getId(block).toString();
        var skillCfg = ConfigManager.whichSkillHasBlock(blockName).orElse(null);
        if (skillCfg == null) {
            AbilityManager.clearReadiedAbility(sp);
            McRPG.getLogger().debug("AbilityEvents: onStartBreak: No skill config for block {}", blockName);
            return;
        }

        Item tool = player.getMainHandStack().getItem();

        switch (readied.get()) {
            case ActiveAbilityType.SUPER_BREAKER -> {
                if (ItemClassifier.isPickaxe(tool) && AbilityManager.canActivate(sp, ActiveAbilityType.SUPER_BREAKER))
                    AbilityManager.activate(sp, ActiveAbilityType.SUPER_BREAKER);
            }
            case ActiveAbilityType.GIGA_DRILL_BREAKER -> {
                if (ItemClassifier.isShovel(tool) && AbilityManager.canActivate(sp, ActiveAbilityType.GIGA_DRILL_BREAKER))
                    AbilityManager.activate(sp, ActiveAbilityType.GIGA_DRILL_BREAKER);
            }
            case ActiveAbilityType.TREE_FELLER -> {
                if (ItemClassifier.isAxe(tool) && AbilityManager.canActivate(sp, ActiveAbilityType.TREE_FELLER))
                    AbilityManager.activate(sp, ActiveAbilityType.TREE_FELLER);
            }
            case ActiveAbilityType.GREEN_TERRA -> {
                if (ItemClassifier.isHoe(tool) && AbilityManager.canActivate(sp, ActiveAbilityType.GREEN_TERRA))
                    AbilityManager.activate(sp, ActiveAbilityType.GREEN_TERRA);
            }
        }
    }
}
