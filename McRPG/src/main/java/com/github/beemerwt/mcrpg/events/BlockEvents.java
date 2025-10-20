package com.github.beemerwt.mcrpg.events;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.persistent.CropMarkers;
import com.github.beemerwt.mcrpg.persistent.FurnaceSlotOwners;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.skills.Excavation;
import com.github.beemerwt.mcrpg.skills.Herbalism;
import com.github.beemerwt.mcrpg.skills.Mining;
import com.github.beemerwt.mcrpg.skills.Woodcutting;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.Random;

// TODO: Smelting implementation

// TODO: Then move on to combat skills and abilities

public final class BlockEvents {
    private static final Random RNG = new Random();

    private BlockEvents() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (world.isClient()) return true;
            if (!(world instanceof ServerWorld sw)) return true;
            if (!(player instanceof ServerPlayerEntity sp)) return true;

            McRPG.getLogger().debug("Processing block break event at {} by player {}", pos, player.getName().getString());

            var block = state.getBlock();
            var blockName = Registries.BLOCK.getId(block).toString();

            if (BlockClassifier.isFurnace(block)) {
                McRPG.getLogger().debug("Removing furnace at {} from smelt automation tracking", pos);
                FurnaceSlotOwners.get(sw).remove(pos);
                return true;
            }

            var skillCfg = ConfigManager.whichSkillHasBlock(blockName).orElse(null);
            if (skillCfg == null) {
                McRPG.getLogger().debug("No skill associated with block " + blockName);
                return true;
            }

            ItemStack tool = player.getMainHandStack();
            List<ItemStack> drops = Block.getDroppedStacks(state, sw, pos, entity, player, tool);

            // Handle crop markers before placed block check, since crops can be both player-placed and naturally spawned
            var marker = CropMarkers.get(sw);
            if (marker.isMarked(pos)) {
                marker.unmark(sw, pos);
                McRPG.getLogger().debug("Removing marked crop {}", blockName);
                Herbalism.onCropBroken(sp, sw, pos, state, drops);
                return true;
            }

            var tracker = PlacedBlockTracker.get(sw);
            if (tracker.isMarked(pos)) {
                tracker.unmark(sw, pos);
                McRPG.getLogger().debug("Skipping block {} because it was player-placed", blockName);
                return true;
            }

            switch (skillCfg.getSkillType()) {
                case MINING -> Mining.onBlockMined(sp, sw, pos, state, drops);
                case WOODCUTTING -> Woodcutting.onLogChopped(sp, sw, pos, state, drops);
                case EXCAVATION -> Excavation.onBlockDug(sp, sw, pos, state, drops);
                case HERBALISM -> Herbalism.onCropBroken(sp, sw, pos, state, drops);
            }

            return true;
        });
    }
}
