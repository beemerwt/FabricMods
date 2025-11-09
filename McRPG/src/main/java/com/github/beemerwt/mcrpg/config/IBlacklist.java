package com.github.beemerwt.mcrpg.config;

import java.util.List;

public interface IBlacklist {
    List<String> getBlacklist();
    default boolean isBlacklisted(String blockId) {
        return getBlacklist().contains(blockId);
    }
}
