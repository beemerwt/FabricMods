package com.github.beemerwt.mcrpg.config;

import java.util.Map;

public interface IHasBlocks {
    Map<String, Integer> getBlocks();

    default boolean hasBlock(String blockId) {
        return getBlocks().containsKey(blockId);
    }
}
