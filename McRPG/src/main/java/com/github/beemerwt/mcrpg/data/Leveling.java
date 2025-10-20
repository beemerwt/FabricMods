package com.github.beemerwt.mcrpg.data;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.GeneralConfig;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.ui.XpBossbarManager;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import static org.joml.Math.lerp;

import java.util.*;

/** Central XP/level logic */
public final class Leveling {
    private Leveling() {}

    public record LevelProgress(int level, float progress /* 0..1 inclusive-exclusive */) {}


    // --------------- Public API ---------------

    /** Add (or subtract) XP by UUID. Returns true on success. */
    public static void addXp(PlayerData data, SkillType skill, long amount) {
        if (skill == null || amount == 0L || data == null) return;
        if (!preGateXpGain(data, skill)) return;

        if (SkillLinks.isAlias(skill)) {
            SkillType primary = SkillLinks.primaryOf(skill);
            if (primary == null) return;
            addXpBase(data, primary, amount);
            return;
        }

        if (SkillLinks.isComposite(skill)) {
            SkillLinks.Composite c = SkillLinks.compositeOf(skill);
            if (c == null) return;

            for (int i = 0; i < c.parts().length; i++) {
                var weight = c.weights()[i];
                var part = c.parts()[i];
                long perPart = Math.round(amount * weight);
                addXpBase(data, part, perPart);
            }

            return;
        }

        // Normal skill
        addXpBase(data, skill, amount);
    }

    public static void setLevel(PlayerData p, SkillType skill, int level) {
        if (p == null || skill == null || level < 0) return;
        setLevelInternal(p, skill, level);
    }

    /** Read effective level */
    public static int getLevel(PlayerData data, SkillType skill) {
        if (data == null || skill == null) return 0;

        if (SkillLinks.isComposite(skill)) {
            SkillLinks.Composite c = SkillLinks.compositeOf(skill);
            if (c == null) {
                McRPG.getLogger().severe("Composite skill has no definition: " + skill);
                return 0;
            }

            return levelFromComposite(data, c);
        }

        GeneralConfig cfg = ConfigManager.getGeneralConfig();
        return levelForTotalXp(getRawTotalXpLocal(data, canonical(skill)), cfg);
    }

    /** Read raw total XP */
    public static long getRawTotalXp(PlayerData data, SkillType skill) {
        if (data == null || skill == null) return 0L;

        return getRawTotalXpLocal(data, canonical(skill));
    }

    // ---------- Convenience Helpers -----------

    public static void addXp(ServerPlayerEntity player, SkillType skill, long amount) {
        if (player == null) return;
        var data = McRPG.getStore().get(player);
        addXp(data, skill, amount);

        long after = getRawTotalXpLocal(data, skill);
        XpBossbarManager.showSkillXp(player, skill, amount, after);
    }

    public static int getLevel(ServerPlayerEntity player, SkillType skill) {
        if (player == null) return 0;
        var data = McRPG.getStore().get(player);
        return getLevel(data, skill);
    }

    // --- per-skill level+progress (works for both normal & alias & composite) ---
    public static LevelProgress getLevelProgress(PlayerData data, SkillType skill) {
        if (data == null || skill == null) return new LevelProgress(0, 0f);
        var cfg = ConfigManager.getGeneralConfig();

        // Composite: weighted average of *continuous* levels
        if (SkillLinks.isComposite(skill)) {
            SkillLinks.Composite c = SkillLinks.compositeOf(skill);
            double weighted = 0.0;
            for (int i = 0; i < c.parts().length; i++) {
                SkillType part = c.parts()[i];
                double w = c.weights()[i];
                long xp = getRawTotalXpLocal(data, canonical(part)); // each source bucket’s total XP
                double Li = continuousLevelForTotalXp(xp, cfg);      // continuous level for that bucket
                weighted += w * Li;
            }
            // clamp and split into integer level + fractional progress
            weighted = Math.max(0.0, Math.min(weighted, cfg.maxLevel));
            int level = (int) Math.floor(weighted);
            float pct  = (float) (weighted - level); // 0..1
            return new LevelProgress(level, pct);
        }

        // Normal / alias path: just one bucket
        SkillType s = canonical(skill);
        long total = getRawTotalXpLocal(data, s);
        double cont = continuousLevelForTotalXp(total, cfg);
        int level = (int) Math.floor(cont);
        float pct = (float) (cont - level);
        return new LevelProgress(level, pct);
    }

    // --------------- Internals ----------------

    private static void addXpBase(PlayerData p, SkillType skill, long amount) {
        if (amount == 0) return;
        SkillType s = canonical(skill);

        long before = p.xp.getOrDefault(s, 0L);
        long after  = clampTotal(before + amount);
        if (after == before) return;

        p.xp.put(s, after);
        p.dirty = true;
    }

    private static void setLevelInternal(PlayerData p, SkillType skill, int level) {
        SkillType s = canonical(skill);
        int clampedLevel = Math.max(0, Math.min(level, ConfigManager.getGeneralConfig().maxLevel));
        long total = totalXpForLevel(clampedLevel);
        setRawTotalXpInternal(p, s, total);
    }

    private static void setRawTotalXpInternal(PlayerData p, SkillType s, long total) {
        long clamped = clampTotal(total);
        Long prev = p.xp.put(s, clamped);
        if (!Objects.equals(prev, clamped)) {
            p.dirty = true;
        }
    }

    private static long getRawTotalXpLocal(PlayerData p, SkillType s) {
        return p.xp.getOrDefault(s, 0L);
    }

    private static SkillType canonical(SkillType s) {
        if (s == null) return null;
        return SkillLinks.isAlias(s) ? Optional.ofNullable(SkillLinks.primaryOf(s)).orElse(s) : s;
    }

    private static boolean preGateXpGain(PlayerData p, SkillType s) {
        // Hard gate: disable XP when at/over configured max level.
        var cfg = ConfigManager.getGeneralConfig();
        int current = levelForTotalXp(getRawTotalXpLocal(p, canonical(s)), cfg);
        return current < cfg.maxLevel;
    }

    // ------------- Curve Helpers --------------

    private static boolean isLinear(GeneralConfig.XpCurve curve) {
        // Preserve previous semantics: "linear" vs "quadratic" etc by base string
        return curve != null && "linear".equalsIgnoreCase(curve.base);
    }

    private static long xpForLevel(int level, GeneralConfig.XpCurve curve) {
        if (isLinear(curve)) {
            // per-level requirement at this level
            return curve.linearBase + (long) level * curve.multiplier;
        }
        // quadratic per-level requirement: a*L^2 + b*L + c
        return curve.quadA * (long) level * (long) level + curve.quadB * (long) level + curve.quadC;
    }

    public static long xpForLevel(int level) {
        return xpForLevel(level, ConfigManager.getGeneralConfig().xpCurve);
    }

    private static double continuousLevelForTotalXp(long total, GeneralConfig cfg) {
        var curve = cfg.xpCurve;
        if (isLinear(curve)) {
            return Math.min(levelFromLinearXP(total, curve.linearBase, curve.multiplier), cfg.maxLevel);
        }
        return Math.min(levelFromQuadraticXP(total, curve.quadA, curve.quadB, curve.quadC), cfg.maxLevel);
    }

    /**
     * Total XP required to reach the given level (from level 0).
     * @param level target level (1..maxLevel)
     * @return total XP required
     */
    public static long totalXpForLevel(int level) {
        if (level <= 0) return 0L;

        var curve = ConfigManager.getGeneralConfig().xpCurve;

        if (isLinear(curve)) {
            // Sum_{i=1..L} (base + i*multiplier) = L*base + multiplier * L*(L+1)/2
            return (long) level * curve.linearBase
                + curve.multiplier * (long) level * ((long) level + 1L) / 2L;
        }

        // Sum_{i=1..L} (a*i^2 + b*i + c)
        long S1 = (long) level * ((long) level + 1L) / 2L;  // 1 + 2 + ... + L
        long S2 = (long) level * ((long) level + 1L) * (2L * (long) level + 1L) / 6L; // 1^2 + 2^2 + ... + L^2
        return curve.quadA * S2 + curve.quadB * S1 + curve.quadC * (long) level;
    }

    /**
     * Gets the highest level for the given total XP.
     * @param total total XP
     * @return level (0..maxLevel)
     */
    public static int levelForTotalXp(long total, GeneralConfig cfg) {
        var curve = cfg.xpCurve;

        if (isLinear(curve)) {
            double lvl = levelFromLinearXP(total, curve.linearBase, curve.multiplier);
            return Math.min((int) Math.floor(lvl), cfg.maxLevel);
        }

        // Quadratic
        double lvl = levelFromQuadraticXP(total, curve.quadA, curve.quadB, curve.quadC);
        return Math.min((int) Math.floor(lvl), cfg.maxLevel);
    }

    private static int levelFromComposite(PlayerData p, @NotNull SkillLinks.Composite c) {
        var cfg = ConfigManager.getGeneralConfig();

        long totalLevel = 0L;
        for (var part : c.parts()) {
            totalLevel += levelForTotalXp(p.xp.get(part), cfg);
        }

        return (int) Math.min(totalLevel / c.parts().length, cfg.maxLevel);
    }

    private static long clampTotal(long v) {
        long maxTotalXp = totalXpForLevel(ConfigManager.getGeneralConfig().maxLevel);
        if (maxTotalXp <= 0) maxTotalXp = Long.MAX_VALUE;
        if (v < 0) return 0L;
        return Math.min(v, maxTotalXp);
    }

    // ------------ Scaling Helpers -------------

    public static float getScaled(float a, float b, int level) {
        float scale = (float) level / (float) ConfigManager.getGeneralConfig().maxLevel;
        return lerp(a, b, scale);
    }

    public static float getScaledPercentage(float a, float b, int level) {
        a = a / 100.0f;
        b = b / 100.0f;
        float scale = (float) level / (float) ConfigManager.getGeneralConfig().maxLevel;
        return lerp(a, b, scale);
    }

    public static long getScaledTicks(long a, long b, int level) {
        float scale = (float) level / (float) ConfigManager.getGeneralConfig().maxLevel;
        float aTicks = (float)a * 20.0f; // convert seconds to ticks
        float bTicks = (float)b * 20.0f;
        return Math.round(lerp(aTicks, bTicks, scale));
    }

    public static long resolveBlockXp(Map<String, Integer> blocks, Block block) {
        var id = Registries.BLOCK.getId(block);

        Integer direct = blocks.get(id.toString());
        if (direct != null) return direct;

        if (!blocks.isEmpty()) {
            RegistryEntry<Block> entry = Registries.BLOCK.getEntry(block);
            for (Map.Entry<String, Integer> e : blocks.entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("#")) continue;
                Identifier tagId = Identifier.tryParse(key.substring(1));
                if (tagId == null) continue;
                TagKey<Block> tagKey = TagKey.of(RegistryKeys.BLOCK, tagId);
                if (entry.isIn(tagKey)) return e.getValue();
            }
        }

        return 0;
    }

    private static double levelFromLinearXP(double total, double base, double mult) {
        total = Math.max(0.0, total);
        if (mult == 0.0) {
            // C(L) = base*L
            return base > 0 ? (total / base) : 0.0;
        }

        // C(L) = base*L + (mult/2)*(L^2 + L)
        // Solve a L^2 + d L - total = 0, d = base + mult/2
        double d = base + (mult * 0.5);
        double radicand = d * d + 2.0 * mult * Math.max(0L, total);
        if (radicand < 0 && radicand > -1e-9) radicand = 0;

        return (-d + Math.sqrt(radicand)) / mult; // positive root
    }

    // Here's some comedic relief
    private static double levelFromQuadraticXP(double total, double a, double b, double c) {
        total = Math.max(0.0, total);

        // Degenerate cases
        if (Math.abs(a) < 1e-12) {
            // Falls back to linear per-level: cost = b*i + c
            // C(L) = (b/2)L^2 + (b/2 + c)L
            double A = b / 2.0;
            double B = (b / 2.0) + c;
            double C = -total;
            if (Math.abs(A) < 1e-12) {
                // C(L) = B L
                return B > 0 ? (total / B) : 0.0;
            }
            double disc = B * B - 4.0 * A * C;
            if (disc < 0 && disc > -1e-9) disc = 0;
            return (-B + Math.sqrt(Math.max(0.0, disc))) / (2.0 * A);
        }

        // Cubic coefficients: α L^3 + β L^2 + γ L + δ = 0
        double alpha = a / 3.0;
        double beta  = (a + b) / 2.0;
        double gamma = a / 6.0 + b / 2.0 + c;
        double delta = -total;

        // Depress the cubic: L = y - beta/(3*alpha)
        double inv3alpha = 1.0 / (3.0 * alpha);
        double shift = beta * inv3alpha;

        // y^3 + p y + q = 0
        double p = (3.0 * alpha * gamma - beta * beta) / (3.0 * alpha * alpha);
        double q = (27.0 * alpha * alpha * delta - 9.0 * alpha * beta * gamma + 2.0 * beta * beta * beta)
            / (27.0 * alpha * alpha * alpha);

        double halfQ = q * 0.5;
        double thirdP = p / 3.0;
        double disc = halfQ * halfQ + thirdP * thirdP * thirdP; // Δ

        double y;
        if (disc >= 0) {
            // One real root
            double sqrtD = Math.sqrt(disc);
            double u = Math.cbrt(-halfQ + sqrtD);
            double v = Math.cbrt(-halfQ - sqrtD);
            y = u + v;
        } else {
            // Three real roots, pick the largest (monotone cumulative implies this is the physical one)
            double r = Math.sqrt(-thirdP);
            double cosArg = -halfQ / (r * r * r);
            // Clamp numerical drift
            cosArg = Math.max(-1.0, Math.min(1.0, cosArg));
            double theta = Math.acos(cosArg);
            // Roots: 2r cos((θ + 2kπ)/3), k = 0,1,2. Largest is k = 0.
            y = 2.0 * r * Math.cos(theta / 3.0);
        }

        double L = y - shift;
        if (L < 0 && L > -1e-9) L = 0; // clean tiny negative
        return Math.max(0.0, L);
    }
}
