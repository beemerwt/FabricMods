package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.config.AbilityConfig;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import net.minecraft.server.MinecraftServer;

import java.util.function.Supplier;

public sealed abstract class Ability<SConfig extends SkillConfig, AConfig extends AbilityConfig>
    permits PassiveAbility, SuperAbility
{
    private final Supplier<AConfig> aConfigSupplier;

    protected SkillType skillType;
    protected SConfig skillConfig;
    protected AConfig config;

    public Ability(SkillType skillType, Supplier<AConfig> aConfigSupplier) {
        this.skillType = skillType;
        this.skillConfig = ConfigManager.getSkillConfig(skillType);
        this.aConfigSupplier = aConfigSupplier;
        this.config = aConfigSupplier.get();
    }

    public SkillType getSkillType() {
        return skillType;
    }

    protected abstract String id();

    public void onReload(SConfig config) {
        this.skillConfig = config;
        this.config = aConfigSupplier.get();
    }

    public abstract void tick(MinecraftServer server);
}
