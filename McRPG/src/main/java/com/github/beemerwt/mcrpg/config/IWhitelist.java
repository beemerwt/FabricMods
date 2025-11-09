package com.github.beemerwt.mcrpg.config;

import java.util.List;

public interface IWhitelist {
    List<String> getWhitelist();
    default boolean isBlockWhitelisted(String blockId) {
        return getWhitelist().contains(blockId);
    }
}
