package com.github.beemerwt.mcrpg.managers;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.SyntaxError;
import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.GeneralConfig;
import com.github.beemerwt.mcrpg.config.IBlockConfig;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.util.FabricLogger;
import com.github.beemerwt.mcrpg.util.JanksonSerde;
import com.github.beemerwt.mcrpg.util.ReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// TODO: Validate all identifiers in configs
//       Potentially use a special type like "BlockString" or "IDString" that does validation on set

public final class ConfigManager {
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("McRPG");
    public static final Path SKILLS_DIR = CONFIG_DIR.resolve("skills");
    private static final String RES_BASE = "mcrpg/defaults/";

    private static final Jankson J = Jankson.builder().build();

    private static final GeneralConfig GENERAL = new GeneralConfig();
    static final Map<SkillType, SkillConfig> BY_SKILL = new EnumMap<>(SkillType.class);

    private static final List<ReloadListener> reloadListeners = new ArrayList<>();

    private ConfigManager() {}

    public static void init() {
        try { Files.createDirectories(SKILLS_DIR); }
        catch (IOException e) { McRPG.getLogger().error(e, "Could not create {}", CONFIG_DIR); }

        try (InputStream in = Files.newInputStream(CONFIG_DIR.resolve("general.json5"))) {
            JsonObject obj = J.load(in);
            JanksonSerde.fillFrom(obj, GENERAL);
            McRPG.getLogger().info("Loaded general.json5");
        } catch (IOException e) {
            McRPG.getLogger().info("No general.json5 found, creating default.");
            try {
                var generalConfig = JanksonSerde.toJson(GENERAL);
                var generalFile = CONFIG_DIR.resolve("general.json5");
                Files.createDirectories(generalFile.getParent());
                Files.writeString(generalFile, generalConfig.toJson(true, true));
            } catch (Exception ex) {
                McRPG.getLogger().error(ex, "Failed to create default general.json5!");
                throw new RuntimeException(ex);
            }
        } catch (SyntaxError e) {
            McRPG.getLogger().error(e, "Syntax error in general config: {}", e.getMessage());
        }

        BY_SKILL.put(SkillType.ACROBATICS, SkillConfig.createOrLoadConfig(SkillType.ACROBATICS));

        // Gathering skills
        BY_SKILL.put(SkillType.MINING, SkillConfig.createOrLoadConfig(SkillType.MINING));
        BY_SKILL.put(SkillType.WOODCUTTING, SkillConfig.createOrLoadConfig(SkillType.WOODCUTTING));
        BY_SKILL.put(SkillType.EXCAVATION, SkillConfig.createOrLoadConfig(SkillType.EXCAVATION));
        BY_SKILL.put(SkillType.HERBALISM, SkillConfig.createOrLoadConfig(SkillType.HERBALISM));

        // Artisan skill
        BY_SKILL.put(SkillType.SMELTING, SkillConfig.createOrLoadConfig(SkillType.SMELTING));
        BY_SKILL.put(SkillType.REPAIR, SkillConfig.createOrLoadConfig(SkillType.REPAIR));
        BY_SKILL.put(SkillType.SALVAGE, SkillConfig.createOrLoadConfig(SkillType.SALVAGE));

        // BY_SKILL.put(SkillType.FISHING, SkillConfig.createOrLoadConfig(SkillType.FISHING));
        // BY_SKILL.put(SkillType.ALCHEMY, SkillConfig.createOrLoadConfig(SkillType.ALCHEMY));

        // Combat skills
        BY_SKILL.put(SkillType.SWORDS, SkillConfig.createOrLoadConfig(SkillType.SWORDS));
        BY_SKILL.put(SkillType.UNARMED, SkillConfig.createOrLoadConfig(SkillType.UNARMED));
        BY_SKILL.put(SkillType.AXES, SkillConfig.createOrLoadConfig(SkillType.AXES));
        // BY_SKILL.put(SkillType.ARCHERY, SkillConfig.createOrLoadConfig(SkillType.ARCHERY));

        FabricLogger.setGlobalDebug(GENERAL.debug);
        McRPG.getLogger().info("Debug logging is {}", GENERAL.debug ? "ENABLED" : "disabled");
        McRPG.getLogger().info("Loaded {} skill configs", BY_SKILL.size());
    }

    public static void reloadAll() {
        McRPG.getLogger().info("Reloading all configs...");
        init();

        for (var listener : reloadListeners) {
            try {
                listener.onReload();
            } catch (Exception ex) {
                McRPG.getLogger().error(ex, "Error during config reload listener {}", listener.getClass().getSimpleName());
            }
        }
    }

    public @NotNull static GeneralConfig getGeneralConfig() { return GENERAL; }

    @SuppressWarnings("unchecked")
    public @NotNull static <T extends SkillConfig> T getSkillConfig(SkillType s) {
        return (T)BY_SKILL.get(s);
    }

    public static boolean isConfiguredBlock(String blockId) {
        return BY_SKILL.values().stream()
                .filter(Objects::nonNull) // in case a load failed and put(null) happened
                .anyMatch(cfg -> cfg instanceof IBlockConfig hb && hb.hasBlock(blockId));
    }

    public static void setDebug(boolean debug) {
        GENERAL.debug = debug;
        FabricLogger.setGlobalDebug(debug);

        McRPG.getLogger().info("Debug logging is now {}", debug ? "ENABLED" : "disabled");

        // Save the setting to general.json5
        try {
            var generalConfig = JanksonSerde.toJson(GENERAL);
            var generalFile = CONFIG_DIR.resolve("general.json5");
            Files.createDirectories(generalFile.getParent());
            Files.writeString(generalFile, generalConfig.toJson(true, true));
        } catch (Exception ex) {
            McRPG.getLogger().error(ex, "Failed to update general.json5!");
        }
    }

    public static void registerReloadListener(ReloadListener listener) {
        reloadListeners.add(listener);
    }
}
