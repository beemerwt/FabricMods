package com.github.beemerwt.essence.core.data;

import com.github.beemerwt.essence.core.Essence;
import net.minecraft.util.Identifier;

public class NetworkIds {
    public static final Identifier C2S_NOCLIP_CAP = Identifier.of(Essence.MOD_ID,"noclip_capability");
    public static final Identifier S2C_NOCLIP_SYNC = Identifier.of(Essence.MOD_ID,"noclip_sync");
    private NetworkIds() {}
}
