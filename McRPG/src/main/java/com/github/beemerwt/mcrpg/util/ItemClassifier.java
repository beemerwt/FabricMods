package com.github.beemerwt.mcrpg.util;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class ItemClassifier {
    private static final Identifier MACE_ITEM = Identifier.of("minecraft", "mace");
    private static final Identifier TRIDENT_ITEM = Identifier.of("minecraft", "trident");

    private static final TagKey<Item> MINING_LOOT = TagKey.of(RegistryKeys.ITEM,
            Identifier.of("minecraft", "enchantable/mining_loot"));

    private static final TagKey<Item> SWORDS = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft:swords"));
    private static final TagKey<Item> AXES = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft:axes"));
    private static final TagKey<Item> BOWS = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft:bows"));
    private static final TagKey<Item> CROSSBOWS = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft:crossbows"));
    private static final TagKey<Item> PICKAXES = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft:pickaxes"));
    private static final TagKey<Item> SHOVELS = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft:shovels"));
    private static final TagKey<Item> HOES = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft:hoes"));

    public static boolean isSword(Item item) {
        RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
        return entry.isIn(SWORDS);
    }

    public static boolean isAxe(Item item) {
        RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
        return entry.isIn(AXES);
    }

    public static boolean isBowLike(Item item) {
        RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
        return entry.isIn(BOWS) || entry.isIn(CROSSBOWS);
    }

    public static boolean isPickaxe(Item item) {
        RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
        return entry.isIn(PICKAXES);
    }

    public static boolean isShovel(Item item) {
        RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
        return entry.isIn(SHOVELS);
    }

    public static boolean isHoe(Item item) {
        RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
        return entry.isIn(HOES);
    }

    public static boolean isTool(Item item) {
        RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
        return entry.isIn(MINING_LOOT);
    }

    public static boolean isTrident(Item item) {
        return Registries.ITEM.getId(item).equals(TRIDENT_ITEM);
    }

    public static boolean isMace(Item item) {
        return Registries.ITEM.getId(item).equals(MACE_ITEM);
    }

    public static String getItemType(Item item) {
        if (isSword(item)) return "sword";
        if (isAxe(item)) return "axe";
        if (isBowLike(item)) return "bow";
        if (isPickaxe(item)) return "pickaxe";
        if (isShovel(item)) return "shovel";
        if (isHoe(item)) return "hoe";
        return "other";
    }
}

