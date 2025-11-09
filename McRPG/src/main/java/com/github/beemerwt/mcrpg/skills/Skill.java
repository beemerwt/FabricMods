package com.github.beemerwt.mcrpg.skills;

import com.github.beemerwt.mcrpg.ability.Ability;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.util.ReloadListener;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public abstract class Skill<C extends SkillConfig> implements ReloadListener {
    protected final SkillType skillType;
    protected C config;

    private final List<Ability<C, ?>> abilities = new ArrayList<>();

    public Skill(SkillType skillType) {
        this.skillType = skillType;
        this.config = ConfigManager.getSkillConfig(skillType);
        ConfigManager.registerReloadListener(this);
    }

    protected <T extends Ability<C, ?>> void addAbility(T a) {
        abilities.add(a);
    }

    public C getConfig() { return config; }

    @Override
    public void onReload() {
        config = ConfigManager.getSkillConfig(this.skillType);
        for (var a : abilities) a.onReload(config);
    }

    public void tick(MinecraftServer server) {
        for (var a : abilities) {
            a.tick(server);
        }
    }
}
