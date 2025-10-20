package com.github.beemerwt.mcrpg.config;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;

import java.util.Map;

@JanksonObject
public class GeneralConfig extends Config {

    @JanksonObject
    public static final class XpCurve {
        @JankComment(value = """
                The base formula for calculating XP needed to level up.
                Options:
                - linear: XP needed increases linearly with level.
                - quad: XP needed increases quadratically with level.
                """)
        public String base = "linear";

        @JankComment("Used only if base is 'linear'. XP needed = base + (level * multiplier)")
        public long linearBase = 1020;
        public long multiplier = 20;

        @JankComment("Used only if base is 'quad'. XP needed = a*L^2 + b*L + c")
        public int quadA = 25;
        public int quadB = 75;
        public int quadC = 150;
    }

    public boolean debug = false;
    public int autoSaveEverySeconds = 300; // 5 minutes

    @JankComment("""
        Permissions System for McRPG.
        Options:
        - None: Permissions are handled internally by McRPG.
        - Fabric: Uses Fabric's permission API.
        - LuckPerms: Uses LuckPerms mod for permissions.
        """
    )
    public String permissions = "None";

    @JankComment("Max level a skill can reach")
    public int maxLevel = 1000;

    public XpCurve xpCurve = new XpCurve();

    @JankComment("""
        Combat XP modifier applied to all XP gains.
        Values > 1.0 increase XP gain, values < 1.0 decrease XP gain.
        """)
    public Map<String, Double> mobXpModifiers = Map.<String, Double>ofEntries(
        Map.entry("minecraft:animals", 1.0),
        Map.entry("minecraft:armadillo", 1.1),
        Map.entry("minecraft:creeper", 4.0),
        Map.entry("minecraft:skeleton", 3.0),
        Map.entry("minecraft:spider", 2.0),
        Map.entry("minecraft:giant", 4.0),
        Map.entry("minecraft:zombie", 2.0),
        Map.entry("minecraft:slime", 2.0),
        Map.entry("minecraft:ghast", 3.0),
        Map.entry("minecraft:happy_ghast", 1.0),
        Map.entry("minecraft:ghastling", 0.5),
        Map.entry("minecraft:pig_zombie", 3.0),
        Map.entry("minecraft:enderman", 1.0),
        Map.entry("minecraft:cave_spider", 3.0),
        Map.entry("minecraft:silverfish", 3.0),
        Map.entry("minecraft:blaze", 3.0),
        Map.entry("minecraft:magma_cube", 2.0),
        Map.entry("minecraft:ender_dragon", 1.0),
        Map.entry("minecraft:wither", 1.0),
        Map.entry("minecraft:witch", 0.1),
        Map.entry("minecraft:iron_golem", 2.0),
        Map.entry("minecraft:wither_skeleton", 4.0),
        Map.entry("minecraft:endermite", 2.0),
        Map.entry("minecraft:guardian", 1.0),
        Map.entry("minecraft:elder_guardian", 4.0),
        Map.entry("minecraft:shulker", 2.0),
        Map.entry("minecraft:donkey", 1.0),
        Map.entry("minecraft:mule", 1.0),
        Map.entry("minecraft:horse", 1.0),
        Map.entry("minecraft:zombie_villager", 2.0),
        Map.entry("minecraft:skeleton_horse", 1.0),
        Map.entry("minecraft:zombie_horse", 1.2),
        Map.entry("minecraft:husk", 3.0),
        Map.entry("minecraft:evoker", 3.0),
        Map.entry("minecraft:polar_bear", 2.0),
        Map.entry("minecraft:llama", 1.0),
        Map.entry("minecraft:vindicator", 3.0),
        Map.entry("minecraft:stray", 2.0),
        Map.entry("minecraft:rabbit", 1.0),
        Map.entry("minecraft:chicken", 1.0),
        Map.entry("minecraft:bat", 1.0),
        Map.entry("minecraft:mushroom_cow", 1.2),
        Map.entry("minecraft:cow", 1.0),
        Map.entry("minecraft:turtle", 1.1),
        Map.entry("minecraft:sheep", 1.0),
        Map.entry("minecraft:pig", 1.0),
        Map.entry("minecraft:squid", 1.0),
        Map.entry("minecraft:ocelot", 1.0),
        Map.entry("minecraft:villager", 1.0),
        Map.entry("minecraft:snowman", 0.0),
        Map.entry("minecraft:parrot", 1.0),
        Map.entry("minecraft:illusioner", 3.0),
        Map.entry("minecraft:drowned", 1.0),
        Map.entry("minecraft:dolphin", 1.3),
        Map.entry("minecraft:phantom", 4.0),
        Map.entry("minecraft:cat", 1.0),
        Map.entry("minecraft:fox", 1.0),
        Map.entry("minecraft:panda", 1.0),
        Map.entry("minecraft:pillager", 2.0),
        Map.entry("minecraft:ravager", 4.0),
        Map.entry("minecraft:trader_llama", 1.0),
        Map.entry("minecraft:wandering_trader", 1.0),
        Map.entry("minecraft:bee", 1.5),
        Map.entry("minecraft:piglin", 2.0),
        Map.entry("minecraft:piglin_brute", 4.5),
        Map.entry("minecraft:hoglin", 4.0),
        Map.entry("minecraft:zombie_pigman", 3.0),
        Map.entry("minecraft:zombified_piglin", 3.0),
        Map.entry("minecraft:strider", 1.2),
        Map.entry("minecraft:zoglin", 2.5),
        Map.entry("minecraft:goat", 1.5),
        Map.entry("minecraft:axolotl", 1.75),
        Map.entry("minecraft:glow_squid", 1.5),
        Map.entry("minecraft:allay", 0.2),
        Map.entry("minecraft:frog", 1.7),
        Map.entry("minecraft:warden", 6.0),
        Map.entry("minecraft:tadpole", 0.2),
        Map.entry("minecraft:sniffer", 1.1),
        Map.entry("minecraft:snifflet", 1.1),
        Map.entry("minecraft:camel", 1.2),
        Map.entry("minecraft:bogged", 2.0),
        Map.entry("minecraft:breeze", 4.0)
    );
}
