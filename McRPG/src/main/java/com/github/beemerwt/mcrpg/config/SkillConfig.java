package com.github.beemerwt.mcrpg.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.SyntaxError;
import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JankIgnore;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;
import com.github.beemerwt.mcrpg.config.skills.*;
import com.github.beemerwt.mcrpg.data.ActiveAbilityType;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.util.JanksonSerde;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@JanksonObject
public class SkillConfig extends Config {
    private static final Path ROOT = ConfigManager.SKILLS_DIR;

    @JankIgnore
    public final SkillType skillType;

    @JankComment("Global XP modifier for this skill.")
    public double xpModifier = 1.0;

    @JankComment("Color of the bossbar shown when obtaining xp.")
    public String bossbarColor = "BLUE"; // <--- default per-skill bar color

    public SkillConfig(SkillType skillType) {
        this.skillType = skillType;
    }

    public SkillType getSkillType() {
        return skillType;
    }

    public boolean hasAbility(ActiveAbilityType activeAbilityType) {
        return false;
    }

    public Optional<AbilityConfig> getAbilityConfig(ActiveAbilityType activeAbilityType) {
        return Optional.empty();
    }

    public static SkillConfig createOrLoadConfig(SkillType skillType) {
        SkillConfig cfg = null;

        var skillName = skillType.name().toLowerCase();
        var skillFile = ROOT.resolve(skillName + ".json5");

        switch (skillType) {
            case ACROBATICS -> cfg = new AcrobaticsConfig();

            // Gathering skills
            case MINING -> cfg = new MiningConfig();
            case WOODCUTTING -> cfg = new WoodcuttingConfig();
            case EXCAVATION -> cfg = new ExcavationConfig();
            case HERBALISM -> cfg = new HerbalismConfig();

            // Artisan skills
            // case FISHING -> cfg = new FishingConfig();
            case SMELTING -> cfg = new SmeltingConfig();
            case REPAIR -> cfg = new RepairConfig();
            case SALVAGE -> cfg = new SalvageConfig();
            // case ALCHEMY -> cfg = new AlchemyConfig();

            // Combat skills
            case SWORDS -> cfg = new SwordsConfig();
            case AXES -> cfg = new AxesConfig();
            case UNARMED -> cfg = new UnarmedConfig();
            //case ARCHERY -> cfg = new ArcheryConfig();
        }

        if (cfg == null) {
            McRPG.getLogger().error("No SkillConfig class for skill {}", skillType);
            return null;
        }

        // Attempt to load existing config
        try (InputStream in = Files.newInputStream(skillFile)) {
            JsonObject obj = J.load(in);
            JanksonSerde.fillFrom(obj, cfg);
            return cfg;
        } catch (IOException | SyntaxError ignored) {}

        // Update old configs with new fields, or create a new one if missing
        try {
            if (!skillFile.toFile().exists())
                McRPG.getLogger().info("No config for {} found, creating default.", skillName);

            var jank = JanksonSerde.toJson(cfg);
            Files.createDirectories(skillFile.getParent());
            Files.writeString(skillFile, jank.toJson(true, true));
        } catch (IOException e) {
            McRPG.getLogger().error(e, "Failed writing new skill config {}", skillFile);
        }

        return cfg;
    }
}
