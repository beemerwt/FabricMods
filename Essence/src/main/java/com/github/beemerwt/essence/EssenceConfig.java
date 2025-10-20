package com.github.beemerwt.essence;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import com.github.beemerwt.annotation.JankComment;
import com.github.beemerwt.annotation.JankIgnore;
import com.github.beemerwt.annotation.JanksonObject;
import com.github.beemerwt.essence.data.StoredLocation;
import com.github.beemerwt.essence.util.Locations;
import com.github.beemerwt.util.JanksonSerde;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@JanksonObject
public class EssenceConfig {
    @JankIgnore
    private final Jankson J = Jankson.builder().build();

    @JankIgnore
    private final Path root = FabricLoader.getInstance().getConfigDir().resolve("Essence");

    @JankIgnore
    private final Path configFile = root.resolve("config.json5");

    @JankComment("""
    Here is where you can specify the permissions system your server uses.
    Options are:
    - None (default): Use the built-in permission system, which is based on operator levels
    - Fabric: Use the Fabric Permissions API (requires Fabric Permissions mod)
    - LuckPerms: Use the LuckPerms native API (requires LuckPerms mod)
    """)
    public String permissions = "None";

    public boolean debug = false;

    @JankComment("The number of seconds a player must wait between using any teleport skill")
    public int teleportCooldownSeconds = 10;

    @JankComment("Time (in seconds) before a pending teleport request expires")
    public int tpaTimeoutSeconds = 60;

    @JankComment("Maximum distance (in blocks) for /tpa requests. Set to 0 to disable.")
    public int tpaMaxDistance = 0;

    @JankComment("The jail time, in minutes, to use for jailing players when no duration is specified.")
    public int defaultJailDuration = 10; // minutes

    @JankComment("The ban time, in minutes, to use for temp-banning players when no duration is specified.")
    public int defaultTempBanDuration = 60; // minutes

    @JankComment("The message shown to players when they are banned when no reason is specified.")
    public String defaultPermanentBanMessage = "You have been permanently banned.";

    @JankComment("The message shown to players when they are temp-banned when no reason is specified.")
    public String defaultTempBanMessage = "You have been temporarily banned.";

    public void load() {
        if (!Files.isRegularFile(configFile)) {
            Essence.getLogger().info("No config.json5 found; generating.");
            save(); // create default
            return;
        }

        try {
            String json = Files.readString(configFile);
            JsonObject root = J.load(json);
            JanksonSerde.fillFrom(root, this);
            Essence.getLogger().info("Loaded config.json5");
        } catch (Exception e) {
            Essence.getLogger().error(e, "Failed to read {}", configFile);
        }
    }

    public void save() {
        try {
            JsonObject root = JanksonSerde.toJson(this);
            String content = root.toJson(true, true); // pretty, JSON5-style
            Files.writeString(
                    configFile,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            Essence.getLogger().error(e, "Failed to write {}", configFile);
        }
    }
}
