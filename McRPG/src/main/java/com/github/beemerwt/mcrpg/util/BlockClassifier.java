package com.github.beemerwt.mcrpg.util;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class BlockClassifier {
    private static final TagKey<Block> ORES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft:ores"));
    private static final TagKey<Block> LEAVES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft:leaves"));
    private static final TagKey<Block> WART_BLOCKS = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft:wart_blocks"));
    private static final TagKey<Block> LOGS = TagKey.of(RegistryKeys.BLOCK, Identifier.of("minecraft:logs"));

    private static final Identifier GOLD_BLOCK = Identifier.of("minecraft:gold_block");
    private static final Identifier IRON_BLOCK = Identifier.of("minecraft:iron_block");

    private static final Identifier FURNACE = Identifier.of("minecraft:furnace");
    private static final Identifier BLAST_FURNACE = Identifier.of("minecraft:blast_furnace");
    private static final Identifier SMOKER = Identifier.of("minecraft:smoker");

    public static boolean isOre(Block block) {
        RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
        return entry.isIn(ORES);
    }

    public static boolean isLeaf(Block block) {
        RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
        return entry.isIn(LEAVES);
    }

    public static boolean isWartBlock(Block block) {
        RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
        return entry.isIn(WART_BLOCKS);
    }

    public static boolean isRoots(Block block) {
        String id = Registries.BLOCK.getId(block).toString();
        return id.equalsIgnoreCase("minecraft:mangrove_roots") ||
                id.equalsIgnoreCase("minecraft:muddy_mangrove_roots");
    }

    public static boolean isLog(Block block) {
        RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
        return entry.isIn(LOGS);
    }

    public static boolean isAnvil(Block block) {
        var blockId = Registries.BLOCK.getId(block);
        return blockId.equals(GOLD_BLOCK) || blockId.equals(IRON_BLOCK);
    }

    public static boolean isIronBlock(Block block) {
        var blockId = Registries.BLOCK.getId(block);
        return blockId.equals(IRON_BLOCK);
    }

    public static boolean isGoldBlock(Block block) {
        var blockId = Registries.BLOCK.getId(block);
        return blockId.equals(GOLD_BLOCK);
    }

    public static boolean isFurnace(Block block) {
        var blockId = Registries.BLOCK.getId(block);
        return blockId.equals(FURNACE) || blockId.equals(BLAST_FURNACE) || blockId.equals(SMOKER);
    }
}
