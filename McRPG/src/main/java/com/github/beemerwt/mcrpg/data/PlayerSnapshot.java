package com.github.beemerwt.mcrpg.data;

public record PlayerSnapshot(PlayerData data, long time, long seq) {
}
