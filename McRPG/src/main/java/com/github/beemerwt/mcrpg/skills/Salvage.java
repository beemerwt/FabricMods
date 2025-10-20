package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.callback.PlayerEvents;
import com.github.beemerwt.mcrpg.config.skills.SalvageConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.text.Component;
import com.github.beemerwt.mcrpg.text.NamedTextColor;
import com.github.beemerwt.mcrpg.util.BlockClassifier;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.util.Messenger;
import com.github.beemerwt.mcrpg.util.SoundUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Salvage {

    private Salvage() {}

    private static final int CONFIRM_TICKS = 40; // 2s at 20 tps
    private static final ConcurrentHashMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(int slot, ItemStack snapshot, long expiresAtTick) {}

    public static void register() {
        PlayerEvents.INTERACT_ITEM.register(Salvage::onItemInteract);
    }

    private static ActionResult onItemInteract(ServerPlayerEntity player, ItemStack itemStack, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!isSalvageable(itemStack)) return ActionResult.PASS;

        // Check player is looking at a gold block within reach
        var range = player.getBlockInteractionRange();
        var raycastResult = player.raycast(range, 0.0f, false);
        if (!(raycastResult instanceof BlockHitResult blockHitResult)) {
            McRPG.getLogger().debug("Repair: onItemInteract: player={}, item={}: no block in sight",
                    player.getName().getString(),
                    itemStack
            );
            return ActionResult.PASS;
        }

        var world = player.getEntityWorld();
        var blockPos = blockHitResult.getBlockPos();
        var blockState = world.getBlockState(blockPos);
        if (blockState == null || !BlockClassifier.isGoldBlock(blockState.getBlock())) {
            McRPG.getLogger().debug("Salvage: onItemInteract: player={}, item={}: not looking at gold block",
                    player.getName().getString(),
                    itemStack
            );
            return ActionResult.PASS;
        }

        trySalvageItem(player, world, itemStack);
        return ActionResult.CONSUME;
    }

    /**
     * Called iff a player right-clicks on a gold block
     */
    public static void trySalvageItem(ServerPlayerEntity player, ServerWorld world, ItemStack stack) {
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        String itemKey = itemId.toString();

        SalvageConfig cfg = ConfigManager.getSkillConfig(SkillType.SALVAGE);
        SalvageConfig.Salvageable sv = cfg.salvageables.get(itemKey);

        if (sv == null) {
            Messenger.actionBar(player, Component.text("This item cannot be salvaged here.", NamedTextColor.RED));
            return;
        }

        // Level gate
        int level = Leveling.getLevel(player, SkillType.SALVAGE);
        if (level < sv.minimumLevel) {
            Messenger.actionBar(player, Component.text("Requires Salvage level " + sv.minimumLevel + ".",
                    NamedTextColor.RED));
            return;
        }

        // Integrity gate (per-item override or global)
        float minIntegrity = (sv.minIntegrityPercent != null ? sv.minIntegrityPercent : cfg.minIntegrityPercent) / 100.0f;
        int max = stack.getMaxDamage();
        int dmg = stack.getDamage();
        float integrity = (max - dmg) / (float) max; // 1.0 = brand new, 0.0 = about to break
        if (integrity < minIntegrity) {
            int pct = Math.round((sv.minIntegrityPercent != null ? sv.minIntegrityPercent : cfg.minIntegrityPercent));
            Messenger.actionBar(player, Component.text("Item is too damaged to salvage (min " + pct + "% integrity).", NamedTextColor.RED));
            return;
        }

        // Confirm flow
        int slot = player.getInventory().getSelectedSlot();
        long now = world.getTime();
        Pending pending = PENDING.get(player.getUuid());
        if (pending == null || pending.expiresAtTick < now || pending.slot != slot
                || !sameItem(stack, pending.snapshot))
        {
            PENDING.put(player.getUuid(), new Pending(slot, stack.copy(), now + CONFIRM_TICKS));
            Messenger.actionBar(player, Component.text("Use again to salvage (destroys item).", NamedTextColor.YELLOW));
            SoundUtil.playSound(player, SoundEvents.BLOCK_SMITHING_TABLE_USE, 0.7f, 1.2f);
            return;
        }

        // Do salvage
        String category = inferCategory(itemId); // Wood/Stone/Iron/Gold/Diamond/Netherite/Leather/String/Other
        Item salvageMat = resolveSalvageMaterial(stack, sv, category);
        if (salvageMat == null) {
            Messenger.actionBar(player, Component.text("No salvage material configured for this item.", NamedTextColor.RED));
            return;
        }

        int baseMaxQty = (sv.maximumQuantity != null) ? sv.maximumQuantity : fallbackMaxQuantity(stack.getItem());
        if (baseMaxQty <= 0) baseMaxQty = 1;

        float returnPct = Leveling.getScaledPercentage(cfg.baseReturnPercent, cfg.maxReturnPercent, level);

        // Integrity reduces yield proportionally
        float effective = returnPct * integrity;

        int qty = Math.max(1, (int) Math.floor(baseMaxQty * effective));
        qty = Math.min(qty, baseMaxQty);

        // Enchant extract chance
        boolean extracted = tryExtractEnchantment(player, world, stack, cfg, level);

        // Give materials
        giveOrDrop(player, new ItemStack(salvageMat, qty));

        // XP award proportional to yield
        float baseXp = cfg.xpModifiers.getOrDefault("Base", 1000.0f);
        float catMul = cfg.xpModifiers.getOrDefault(category, cfg.xpModifiers.getOrDefault("Other", 1.0f));
        long xp = Math.round(baseXp * catMul * sv.xpMultiplier);
        Leveling.addXp(player, SkillType.SALVAGE, xp);

        // Consume the item being salvaged (destroy one from the stack)
        stack.decrement(1);

        // Feedback
        String msg = "Salvaged " + qty + "x " + Registries.ITEM.getId(salvageMat).getPath()
                + (extracted ? " + Enchant Book" : "")
                + " for +" + xp + " XP.";
        Messenger.actionBar(player, Component.text(msg, NamedTextColor.GREEN));
        SoundUtil.playSound(player, SoundEvents.BLOCK_ANVIL_DESTROY, 0.9f, extracted ? 1.25f : 1.0f);

        PENDING.remove(player.getUuid());
    }

    // ---------------- helpers ----------------
    private static boolean isSalvageable(ItemStack stack) {
        SalvageConfig cfg = ConfigManager.getSkillConfig(SkillType.SALVAGE);
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return cfg.salvageables.containsKey(id.toString());
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (!a.isOf(b.getItem())) return false;
        if (a.getDamage() != b.getDamage()) return false;
        if (a.getCount() != b.getCount()) return false;
        // Components are the NBT replacement (1.20.5+)
        return Objects.equals(a.getComponents(), b.getComponents());
    }

    private static String inferCategory(Identifier itemId) {
        // Maps to keys in cfg.xpModifiers: Wood/Stone/Iron/Gold/Diamond/Netherite/Leather/String/Other
        String path = itemId.getPath(); // e.g. wooden_pickaxe
        if (path.startsWith("wooden_")) return "Wood";
        if (path.startsWith("stone_")) return "Stone";
        if (path.startsWith("iron_")) return "Iron";
        if (path.startsWith("golden_")) return "Gold";
        if (path.startsWith("diamond_")) return "Diamond";
        if (path.startsWith("netherite_")) return "Netherite";
        if (path.startsWith("leather_")) return "Leather";
        if (path.equals("bow") || path.equals("crossbow") || path.equals("fishing_rod") || path.equals("carrot_on_a_stick")) {
            return "String";
        }
        return "Other";
    }

    private static int fallbackMaxQuantity(Item item) {
        String k = Registries.ITEM.getId(item).getPath();
        if (k.contains("sword")) return 2;
        if (k.contains("pickaxe") || k.contains("axe")) return 3;
        if (k.contains("hoe")) return 2;
        if (k.contains("shovel")) return 1;
        if (k.contains("helmet")) return 5;
        if (k.contains("chestplate")) return 8;
        if (k.contains("leggings")) return 7;
        if (k.contains("boots")) return 4;
        if (k.contains("shield")) return 6;
        return 2;
    }

    private static Item resolveSalvageMaterial(ItemStack stack, SalvageConfig.Salvageable sv, String category) {
        String idStr = (sv.salvageMaterial != null && !sv.salvageMaterial.isBlank())
                ? sv.salvageMaterial
                : switch (category) {
            case "Wood" -> "minecraft:oak_planks";
            case "Stone" -> "minecraft:cobblestone";
            case "Iron" -> "minecraft:iron_ingot";
            case "Gold" -> "minecraft:gold_ingot";
            case "Diamond" -> "minecraft:diamond";
            case "Netherite" -> "minecraft:netherite_scrap";
            case "Leather" -> "minecraft:leather";
            case "String" -> "minecraft:string";
            default -> "";
        };
        if (idStr.isEmpty()) return null;
        return Registries.ITEM.get(Identifier.tryParse(idStr));
    }

    private static boolean tryExtractEnchantment(ServerPlayerEntity player, ServerWorld world, ItemStack stack,
                                                 SalvageConfig cfg, int level) {
        // 1.20.5+ components
        var comp = stack.getEnchantments();
        var set = comp.getEnchantments();
        if (set == null || set.isEmpty()) return false;

        float chance = Leveling.getScaledPercentage(cfg.baseExtractChance, cfg.maxExtractChance, level);
        if (world.getRandom().nextFloat() >= chance) return false;

        // Pick one random enchantment to extract
        ArrayList<RegistryEntry<Enchantment>> list = new ArrayList<>(set);
        RegistryEntry<Enchantment> pick = list.get(world.getRandom().nextInt(list.size()));
        int lvl = comp.getLevel(pick);

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        // If your Yarn changes, swap this for EnchantedBookItem.addEnchantment(book, new EnchantmentLevelEntry(...))
        book.addEnchantment(pick, lvl);

        giveOrDrop(player, book);
        return true;
    }

    private static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        boolean inserted = player.getInventory().insertStack(stack.copy());
        if (!inserted || !stack.isEmpty()) {
            player.dropItem(stack, false, false);
        }
    }
}
