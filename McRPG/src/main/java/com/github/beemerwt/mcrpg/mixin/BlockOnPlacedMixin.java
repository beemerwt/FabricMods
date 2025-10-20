package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.HerbalismConfig;
import com.github.beemerwt.mcrpg.persistent.CropMarkers;
import com.github.beemerwt.mcrpg.persistent.FurnaceSlotOwners;
import com.github.beemerwt.mcrpg.persistent.PlacedBlockTracker;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.text.Component;
import com.github.beemerwt.mcrpg.text.NamedTextColor;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.util.Messenger;
import com.github.beemerwt.mcrpg.util.SoundUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockOnPlacedMixin {
    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void onBlockPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (!(world instanceof ServerWorld sw)) return;
        if (!(placer instanceof ServerPlayerEntity sp)) return;

        var block = state.getBlock();
        if (BlockClassifier.isFurnace(block)) {
            FurnaceSlotOwners.get(sw).setAll(pos, sp.getUuid());
            return;
        }

        if (BlockClassifier.isAnvil(block)) {
            McRPG.getLogger().debug("Player {} placed an anvil at {}", sp.getName().getString(), pos);

            if (BlockClassifier.isIronBlock(block)) {
                // These blocks are used in custom crafting recipes and should not be tracked.
                SoundUtil.playSound(sp, SoundEvents.BLOCK_ANVIL_PLACE, 1.0f, 0.3f);
                Messenger.actionBar(sp, Component.text("You have placed an anvil. Anvils can repair tools and armor.",
                        NamedTextColor.GREEN));
                return;
            }

            if (BlockClassifier.isGoldBlock(block)) {
                // These blocks are used in custom crafting recipes and should not be tracked.
                SoundUtil.playSound(sp, SoundEvents.BLOCK_ANVIL_PLACE, 1.0f, 0.3f);
                Messenger.actionBar(sp, Component.text("You have placed a salvage anvil. Use it to salvage tools and armor.",
                        NamedTextColor.GREEN));
                return;
            }
        }

        // Only track placements that are relevant to a skill (reduces save size)
        String id = Registries.BLOCK.getId(block).toString();
        var cfg = ConfigManager.whichSkillHasBlock(id).orElse(null);
        if (cfg == null) {
            McRPG.getLogger().debug("Placed block {} is not tracked by any skill.", id);
            return;
        }

        if (cfg instanceof HerbalismConfig herbCfg) {
            // TODO: Mark crops that don't have an age with -1 modifier
            //       So we can ignore them when awarding xp

            int level = Leveling.getLevel(sp, SkillType.HERBALISM); // ensure level is cached
            float growthModifier = Leveling.getScaled(herbCfg.greenThumb.baseGrowthMultiplier,
                    herbCfg.greenThumb.maxGrowthMultiplier, level);

            var cm = CropMarkers.get(sw);
            cm.mark(sw, pos, growthModifier);
            cm.mark(sw, pos.down(), growthModifier);

            long k = pos.asLong();
            boolean hasHere  = cm.containsKey(k);
            boolean hasBelow = cm.containsKey(pos.down().asLong());

            McRPG.getLogger().debug("onPlaced: world={} wHash={} cmHash={} size={}" +
                            "pos={} long={} wrote={} hasHere={} hasBelow={}",
                    sw.getRegistryKey().getValue(),
                    System.identityHashCode(sw),
                    System.identityHashCode(cm),
                    cm.size(),
                    pos, k, growthModifier,
                    hasHere, hasBelow
            );
            return;
        }

        PlacedBlockTracker.get(sw).mark(sw, pos);
    }
}
