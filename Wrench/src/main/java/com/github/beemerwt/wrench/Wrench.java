package com.github.beemerwt.wrench;

import com.github.beemerwt.resourcelib.ResourceApi;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.*;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.util.List;

public final class Wrench implements DedicatedServerModInitializer, EventRegistrar {
    private static final Logger LOG = LoggerFactory.getLogger("Wrench");

    public static final int CUSTOM_MODEL_DATA = 217654;
    public static CustomModelDataComponent MODEL_DATA_COMPONENT = new CustomModelDataComponent(
            List.of((float) CUSTOM_MODEL_DATA), List.of(), List.of("wrench"), List.of());

    public static WrenchConfig CONFIG;

    @Override
    public void onInitializeServer() {
        LOG.info("[Wrench] Initializing...");
        CONFIG = WrenchConfig.load();

        // Register the ResourceApi
        ResourceApi api = ResourceApi.api().orElseThrow(() -> {
            LOG.error("[Wrench] Wrench requires ResourceLib to function. Please install ResourceLib.");
            return new IllegalStateException("Wrench requires ResourceLib to function. Please install ResourceLib.");
        });

        // Add the java and bedrock packs
        var dir = api.getPackDirectory().resolve("Wrench");
        api.addBedrockPack(new File(dir.resolve("Wrench-Bedrock.zip").toUri()));
        api.addJavaPack(new File(dir.resolve("Wrench-Java.zip").toUri()));

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                WrenchCommands.registerCommands(dispatcher));
        UseBlockCallback.EVENT.register(this::onUseBlock);
        GeyserApi.api().eventBus().subscribe(this, GeyserDefineCustomItemsEvent.class, this::onDefineCustomItems);
    }

    private ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (!isWrench(stack)) return ActionResult.PASS;

        // require_sneak gate
        if (CONFIG.requireSneak && !player.isSneaking()) {
            return ActionResult.PASS; // let GUIs open normally if they click w/o sneaking
        }

        BlockPos pos = hit.getBlockPos();
        BlockState before = world.getBlockState(pos);

        // Only act on configured machines
        Identifier id = Registries.BLOCK.getId(before.getBlock());
        if (!CONFIG.isMachine(id)) {
            return ActionResult.PASS; // not our machine: let vanilla behavior happen
        }

        // Apply rotation (sneak still controls reverse direction)
        BlockState after = RotationUtil.rotate(before, player.isSneaking());
        if (after != null && after != before) {
            boolean ok = world.setBlockState(pos, after, net.minecraft.block.Block.NOTIFY_ALL);
            if (ok) {
                world.playSound(null, pos, SoundEvents.ITEM_SPYGLASS_USE, SoundCategory.BLOCKS, 0.6f, 1.2f);
                // cooldown from config
                player.getItemCooldownManager().set(stack, Math.max(0, CONFIG.cooldownTicks));
                return ActionResult.CONSUME; // prevent GUI
            }
        }

        // If it's a machine but nothing changed, still consume to avoid opening a GUI while wrenching
        return ActionResult.CONSUME;
    }

    @Subscribe
    public void onDefineCustomItems(GeyserDefineCustomItemsEvent event) {
        CustomItemOptions opts = CustomItemOptions.builder()
                .customModelData(CUSTOM_MODEL_DATA)
                .build();

        CustomItemData wrench = CustomItemData.builder()
                .name("wrench")    // internal id
                .displayName("Wrench")
                .icon("wrench")    // must match textures/item_texture.json in Bedrock pack
                .customItemOptions(opts)
                .build();

        boolean ok = event.register("minecraft:copper_ingot", wrench);
        LOG.info("Registered Geyser custom item mapping (copper_ingot -> copper_wrench): {}", ok);
    }

    private static boolean isWrench(ItemStack stack) {
        var model = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return MODEL_DATA_COMPONENT.equals(model);
    }
}
