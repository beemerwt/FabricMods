package com.github.beemerwt.mcrpg.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

public class SoundUtil {
    public static void playSound(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        player.playSoundToPlayer(sound, SoundCategory.PLAYERS, volume, pitch);
    }
}
