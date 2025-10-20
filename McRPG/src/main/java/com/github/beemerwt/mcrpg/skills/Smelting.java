package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.callback.FurnaceEvents;
import com.github.beemerwt.mcrpg.callback.ScreenHandlerEvents;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.skills.SmeltingConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.persistent.FurnaceSlotOwners;
import com.github.beemerwt.mcrpg.proxies.AbstractFurnaceBlockEntityProxy;
import com.github.beemerwt.mcrpg.text.Component;
import com.github.beemerwt.mcrpg.text.NamedTextColor;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.util.Messenger;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.FuelRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.Math;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public final class Smelting {
    private record MoveAction(ItemStack input, ItemStack fuel) {}
    private static final Map<ScreenHandler, MoveAction> PRE_MOVE = new IdentityHashMap<>();

    private Smelting() {}

    public static void register() {
        ScreenHandlerEvents.BEFORE_SLOT_CLICK.register(Smelting::onBeforeItemChange);
        ScreenHandlerEvents.AFTER_SLOT_CLICK.register(Smelting::onAfterItemChange);
        ScreenHandlerEvents.BEFORE_QUICK_MOVE.register(Smelting::onBeforeItemChange);
        ScreenHandlerEvents.AFTER_QUICK_MOVE.register(Smelting::onAfterItemChange);
        ScreenHandlerEvents.CLOSED.register(Smelting::onScreenClosed);

        FurnaceEvents.ITEM_SMELTED.register(Smelting::onItemSmelted);
        FurnaceEvents.FUEL_CONSUMED.register(Smelting::onFuelConsumed);
    }

    private static void onFuelConsumed(AbstractFurnaceBlockEntityProxy proxy, ItemStack fuel) {
        if (!(proxy.world() instanceof ServerWorld sw)) return;
        var pos = proxy.pos();
        var ownerId = FurnaceSlotOwners.get(sw).getFuelOwner(pos);
        if (ownerId == null) {
            McRPG.getLogger().debug("Couldn't find owner for furnace at {}", pos);
            return;
        }

        FuelRegistry fuelRegistry = sw.getFuelRegistry();
        if (!fuelRegistry.isFuel(fuel)) {
            McRPG.getLogger().warning("Item in furnace at {} was not valid fuel", pos);
            return;
        }

        int addedTicks = fuelRegistry.getFuelTicks(fuel);
        if (addedTicks <= 0) {
            McRPG.getLogger().warning("Fuel in furnace at {} had no fuel value", pos);
            return;
        }

        SmeltingConfig cfg = ConfigManager.getSkillConfig(SkillType.SMELTING);
        var data = McRPG.getStore().get(ownerId);
        int smeltingLevel = Leveling.getLevel(data, SkillType.SMELTING);
        float fuelMult = Leveling.getScaled(cfg.baseFuelEfficiencyMultiplier,
                cfg.maxFuelEfficiencyMultiplier, smeltingLevel);

        addedTicks = (int) Math.floor(addedTicks * (fuelMult - 1.0f));
        proxy.addFuelTime(addedTicks);

        McRPG.getLogger().debug("Added {} ticks to furnace at {} for player {}", addedTicks, pos, ownerId);
    }

    private static void onItemSmelted(AbstractFurnaceBlockEntityProxy proxy,
                                      ItemStack input, ItemStack output) {
        if (!(proxy.world() instanceof ServerWorld sw)) return;
        var pos = proxy.pos();
        var ownerId = FurnaceSlotOwners.get(sw).getInputOwner(pos);
        if (ownerId == null) {
            McRPG.getLogger().debug("Couldn't find owner for furnace at {}", pos);
            return;
        }

        McRPG.getLogger().debug("Player {} smelted item {} from {}", ownerId, output, input);

        SmeltingConfig cfg = ConfigManager.getSkillConfig(SkillType.SMELTING);

        var data = McRPG.getStore().get(ownerId);
        int smeltingLevel = Leveling.getLevel(data, SkillType.SMELTING);
        float xpMult = Leveling.getScaled(cfg.baseXpMultiplier, cfg.maxXpMultiplier, smeltingLevel);

        var key = Registries.ITEM.getId(input.getItem()).toString();
        int baseXp = cfg.items.getOrDefault(key, 0);
        if (baseXp <= 0) {
            McRPG.getLogger().debug("No XP configured for smelting item {}", key);
            return;
        }

        long award = Math.round(baseXp * xpMult);
        if (trySecondSmelt(ownerId, output))
            award *= 2;

        ServerPlayerEntity sp = McRPG.getServer().getPlayerManager().getPlayer(ownerId);
        if (sp != null)
            Leveling.addXp(sp, SkillType.SMELTING, award);
        else
            Leveling.addXp(data, SkillType.SMELTING, award);
    }

    // Called by furnace mixin to decide whether to double the result and (optionally) message
    public static boolean trySecondSmelt(UUID ownerId, ItemStack output) {
        SmeltingConfig cfg = ConfigManager.getSkillConfig(SkillType.SMELTING);

        var data = McRPG.getStore().get(ownerId);
        int smeltingLevel = Leveling.getLevel(data, SkillType.SMELTING);
        float chance = Leveling.getScaledPercentage(cfg.doubleDrops.baseChance,
                cfg.doubleDrops.maxChance, smeltingLevel);

        boolean proc = Math.random() < (double) chance;

        if (proc && ownerId != null) {
            ServerPlayerEntity sp = McRPG.getServer().getPlayerManager().getPlayer(ownerId);

            if (output.getCount() < output.getMaxCount()) {
                McRPG.getLogger().debug("Doubled smelt output for player {}", ownerId);
                output.increment(1);
            } else {
                McRPG.getLogger().debug("Couldn't double smelt output for player {}: full stack", ownerId);
            }

            if (sp != null) {
                // light feedback (no title spam); easy to toggle off later
                Messenger.actionBar(sp, Component.text("Second Smelt!", NamedTextColor.YELLOW));
            }
        }

        return proc;
    }

    private static void onBeforeItemChange(ScreenHandler handler, ServerWorld world,
                                           ServerPlayerEntity player, int index)
    {
        if (!isFurnaceHandler(handler)) return;
        var input = handler.slots.get(0);
        var fuel = handler.slots.get(1);
        PRE_MOVE.put(handler, new MoveAction(input.getStack().copy(), fuel.getStack().copy()));
    }

    private static void onAfterItemChange(ScreenHandler handler, ServerWorld world,
                                          ServerPlayerEntity player, int index)
    {
        if (!isFurnaceHandler(handler)) return;
        var action = PRE_MOVE.remove(handler);
        if (action == null) return;

        var input = handler.slots.get(0);
        var fuel = handler.slots.get(1);

        if (!ItemStack.areItemsAndComponentsEqual(input.getStack(), action.input)) {
            McRPG.getLogger().debug("Player {} changed input slot in furnace", player.getName());
            setOwnerForSlot(input, player);
            return;
        }

        if (!ItemStack.areItemsAndComponentsEqual(fuel.getStack(), action.fuel)) {
            McRPG.getLogger().debug("Player {} changed fuel slot in furnace", player.getName());
            setOwnerForSlot(fuel, player);
            return;
        }

        if (input.getStack().getCount() > action.input.getCount()) {
            McRPG.getLogger().debug("Player {} added item to furnace", player.getName());
            setOwnerForSlot(input, player);
            return;
        }

        if (fuel.getStack().getCount() > action.fuel.getCount()) {
            McRPG.getLogger().debug("Player {} added fuel to furnace", player.getName());
            setOwnerForSlot(fuel, player);
        }
    }

    public static boolean isFurnaceHandler(ScreenHandler handler) {
        return handler instanceof AbstractFurnaceScreenHandler;
    }

    private static void setOwnerForSlot(Slot s, ServerPlayerEntity player) {
        if (!(s.inventory instanceof AbstractFurnaceBlockEntity be)) {
            McRPG.getLogger().debug("Tried to set furnace slot owner, but inventory was not furnace");
            return;
        }

        ServerWorld sw = player.getEntityWorld();
        BlockPos pos = be.getPos();

        int slot = s.getIndex();
        if (slot == 0) {
            FurnaceSlotOwners.get(sw).setInput(pos, player.getUuid());
        } else if (slot == 1) {
            FurnaceSlotOwners.get(sw).setFuel(pos, player.getUuid());
        } else if (slot == 2) {
            FurnaceSlotOwners.get(sw).setFuel(pos, player.getUuid());
        }
    }

    public static void onScreenClosed(ScreenHandler handler, ServerPlayerEntity player) {
        if (!isFurnaceHandler(handler)) return;
        PRE_MOVE.remove(handler);
    }
}
