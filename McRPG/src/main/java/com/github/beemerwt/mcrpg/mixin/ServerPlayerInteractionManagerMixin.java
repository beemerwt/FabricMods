package com.github.beemerwt.mcrpg.mixin;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.callback.PlayerEvents;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    public void mcrpg$interactItem(
            ServerPlayerEntity player, World world, ItemStack stack,
            Hand hand, CallbackInfoReturnable<ActionResult> cir
    ) {
        McRPG.getLogger().debug("interact item: player={}, item={}, hand={}",
                player.getName().getString(),
                stack,
                hand
        );

        var result = PlayerEvents.INTERACT_ITEM.invoker().onInteractItem(player, stack, hand);
        if (result != ActionResult.PASS) {
            // If the event returned SUCCESS or FAIL, we need to resync the hand and armor
            if (stack.isIn(ItemTags.EQUIPPABLE_ENCHANTABLE)) {
                EquipmentSlot armorSlot = null;
                if (stack.isIn(ItemTags.HEAD_ARMOR))
                    armorSlot = EquipmentSlot.HEAD;
                else if (stack.isIn(ItemTags.CHEST_ARMOR))
                    armorSlot = EquipmentSlot.CHEST;
                else if (stack.isIn(ItemTags.LEG_ARMOR))
                    armorSlot = EquipmentSlot.LEGS;
                else if (stack.isIn(ItemTags.FOOT_ARMOR))
                    armorSlot = EquipmentSlot.FEET;

                if (armorSlot != null)
                    resyncHandAndArmor(player, hand, stack, armorSlot);
            }
            cir.setReturnValue(result);
        }
    }

    @Unique
    private static void resyncHandAndArmor(ServerPlayerEntity p, Hand hand, ItemStack realHandStack, EquipmentSlot predictedArmorSlot) {
        // 1) Correct the hand slot (hotbar index: 36..44)
        int handIdx = (hand == Hand.MAIN_HAND) ? p.getInventory().getSelectedSlot() : 40; // 40 = offhand logical index in PlayerScreenHandler
        int handlerSlot = (hand == Hand.MAIN_HAND) ? (36 + handIdx) : 45; // 45 = offhand in PlayerScreenHandler

        // Make sure the server-side stack is what you intend (often just p.getStackInHand(hand))
        p.getInventory().markDirty();
        p.playerScreenHandler.sendContentUpdates(); // general refresh (ok to keep)
        p.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                p.playerScreenHandler.syncId,
                p.playerScreenHandler.getRevision(),
                handlerSlot,
                realHandStack.copy()
        ));

        // 2) Correct the armor slot the client might have “equipped”
        // Send just the one slot you expect the client to have predicted
        ItemStack realEquipped = p.getEquippedStack(predictedArmorSlot);
        p.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(
                p.getId(),
                List.of(new Pair<>(predictedArmorSlot, realEquipped.copy()))
        ));
    }
}
