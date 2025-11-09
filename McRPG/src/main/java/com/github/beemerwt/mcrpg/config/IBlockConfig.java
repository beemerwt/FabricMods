package com.github.beemerwt.mcrpg.config;

import java.util.Map;

public interface IBlockConfig {
    Map<String, Integer> getBlocks();

    default boolean hasBlock(String blockId) {
        return getBlocks().containsKey(blockId);
    }
}
