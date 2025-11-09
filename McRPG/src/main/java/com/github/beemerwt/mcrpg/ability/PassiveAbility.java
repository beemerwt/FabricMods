package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.config.AbilityConfig;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import net.minecraft.server.MinecraftServer;

import java.util.function.Supplier;

public non-sealed abstract class PassiveAbility<SConfig extends SkillConfig, AConfig extends AbilityConfig>
    extends Ability<SConfig, AConfig>
{
    public PassiveAbility(SkillType type, Supplier<AConfig> aConfigSupplier) {
        super(type, aConfigSupplier);
    }

    @Override
    public void tick(MinecraftServer server) {
        // Passive abilities do not have periodic behavior by default
    }
}
