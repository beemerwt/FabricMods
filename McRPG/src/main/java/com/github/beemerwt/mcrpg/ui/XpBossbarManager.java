package com.github.beemerwt.mcrpg.ui;

import com.github.beemerwt.mcrpg.McRPG;
import com.github.beemerwt.mcrpg.config.GeneralConfig;
import com.github.beemerwt.mcrpg.data.PlayerData;
import com.github.beemerwt.mcrpg.data.SkillLinks;
import com.github.beemerwt.mcrpg.managers.ConfigManager;
import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.text.NamedTextColor;
import com.github.beemerwt.mcrpg.util.SoundUtil;
import com.github.beemerwt.mcrpg.data.Leveling;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;

public final class XpBossbarManager {
    private static final int DISPLAY_TICKS = 60; // ~3 seconds at 20 tps

    // Per-player -> per-skill bossbar
    private static final Map<UUID, EnumMap<SkillType, ServerBossBar>> bars = new HashMap<>();
    // Time-to-live for each bar instance
    private static final Map<ServerBossBar, Integer> ttl = new HashMap<>();

    private XpBossbarManager() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(XpBossbarManager::onEndTick);

        // Ensure we clean up bars when a player leaves (sends REMOVE packets automatically)
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity sp = handler.player;
            EnumMap<SkillType, ServerBossBar> map = bars.remove(sp.getUuid());
            if (map != null) {
                for (ServerBossBar bar : map.values()) {
                    bar.clearPlayers(); // sends BossBar remove packets
                    ttl.remove(bar);
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((disp, reg, env) -> {
            disp.register(CommandManager.literal("mcrpg_testbar")
                    .executes(ctx -> {
                        ServerPlayerEntity sp = ctx.getSource().getPlayer();
                        XpBossbarManager.showSkillXp(sp, SkillType.MINING, 25, 500);
                        return 1;
                    }));
        });
    }

    private static void onEndTick(MinecraftServer server) {
        if (ttl.isEmpty()) return;

        List<ServerBossBar> toRemove = new ArrayList<>();
        ttl.replaceAll((bar, t) -> t - 1);
        for (var e : ttl.entrySet()) if (e.getValue() <= 0) toRemove.add(e.getKey());
        if (toRemove.isEmpty()) return;

        for (ServerBossBar bar : toRemove) {
            bar.clearPlayers(); // sends remove packets to any attached players
            ttl.remove(bar);
            bars.values().forEach(m -> m.values().removeIf(b -> b == bar));
        }
    }

    public static void showSkillXp(ServerPlayerEntity sp, SkillType skill, long justAdded, long newTotalXp, boolean playSound) {
        if (sp == null || skill == null || justAdded == 0) return;

        final PlayerData data = McRPG.getStore().get(sp);
        final GeneralConfig cfg = ConfigManager.getGeneralConfig();
        final SkillConfig skillCfg = ConfigManager.getSkillConfig(skill);
        final int maxLevel = cfg.maxLevel;

        // AFTER state (already applied by Leveling.addXp)
        Leveling.LevelProgress afterProg = Leveling.getLevelProgress(data, skill);
        int   levelAfter = afterProg.level();
        float pctAfter   = afterProg.progress();

        // BEFORE state (reconstruct, do not mutate storage)
        int levelBefore;
        float pctBefore;

        if (SkillLinks.isComposite(skill)) {
            SkillLinks.Composite c = SkillLinks.compositeOf(skill);

            double weightedBefore = 0.0;
            for (int i = 0; i < c.parts().length; i++) {
                SkillType part = c.parts()[i];
                double w = c.weights()[i];

                long totalAfter  = Leveling.getRawTotalXp(data, part);
                long routedDelta = Math.round(justAdded * w);
                long totalBefore = Math.max(0L, totalAfter - routedDelta);

                Leveling.LevelProgress lpPartBefore = progressFromTotal(totalBefore, cfg);
                weightedBefore += w * continuous(lpPartBefore);
            }

            weightedBefore = Math.max(0.0, Math.min(weightedBefore, maxLevel));
            levelBefore = (int) Math.floor(weightedBefore);
            pctBefore   = (float) (weightedBefore - levelBefore);
        } else {
            // Normal / alias: use raw totalBefore with cap pinning
            long totalBefore = Math.max(0L, newTotalXp - justAdded);
            long capXp = Leveling.totalXpForLevel(maxLevel);
            if (newTotalXp >= capXp && levelAfter >= maxLevel) {
                totalBefore = Math.max(totalBefore, capXp);
            }
            Leveling.LevelProgress lpBefore = progressFromTotal(totalBefore, cfg);
            levelBefore = lpBefore.level();
            pctBefore   = lpBefore.progress();
        }

        // Play sound only on a real level-up. Suppress repeats at cap.
        if (playSound && levelAfter > levelBefore) {
            if (levelAfter >= maxLevel) {
                if (levelBefore < maxLevel) {
                    SoundUtil.playSound(sp, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            } else {
                if (levelAfter % 100 == 0) {
                    SoundUtil.playSound(sp, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                } else {
                    SoundUtil.playSound(sp, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
                }
            }
        }

        // Title: "Mining Lv.5"
        String niceName = skill.name().charAt(0) + skill.name().substring(1).toLowerCase(Locale.ROOT);
        Text title = Text.literal(niceName + " Lv.").append(
                Text.literal(String.valueOf(levelAfter)).withColor(NamedTextColor.GOLD.value()));

        ServerBossBar bar = bars
                .computeIfAbsent(sp.getUuid(), id -> new EnumMap<>(SkillType.class))
                .computeIfAbsent(skill, s -> new ServerBossBar(title, parseColor(skillCfg.bossbarColor), BossBar.Style.PROGRESS));

        bar.addPlayer(sp);
        bar.setName(title);
        bar.setColor(parseColor(skillCfg.bossbarColor));
        bar.setPercent(levelAfter >= maxLevel ? 1.0f : pctAfter);

        // Keep it alive while XP is flowing
        ttl.put(bar, DISPLAY_TICKS);
    }

    /** Convert a raw total XP into (level, progress) using only public Leveling APIs. */
    private static Leveling.LevelProgress progressFromTotal(long total, GeneralConfig cfg) {
        final int maxLevel = cfg.maxLevel;
        int L = Leveling.levelForTotalXp(total, cfg);
        if (L >= maxLevel) {
            return new Leveling.LevelProgress(maxLevel, 1.0f);
        }
        long at = Leveling.totalXpForLevel(L);
        long nx = Leveling.totalXpForLevel(L + 1);
        float frac = (nx == at) ? 0f : (float) Math.max(0d, Math.min(1d, (double)(total - at) / (double)(nx - at)));
        return new Leveling.LevelProgress(L, frac);
    }

    /** Continuous representation: level + fractional progress. */
    private static double continuous(Leveling.LevelProgress lp) {
        return (double) lp.level() + (double) lp.progress();
    }

    public static void showSkillXp(ServerPlayerEntity sp, SkillType skill, long justAdded, long newTotalXp) {
        showSkillXp(sp, skill, justAdded, newTotalXp, true);
    }

    private static BossBar.Color parseColor(String s) {
        if (s == null) return BossBar.Color.BLUE;
        try { return BossBar.Color.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { return BossBar.Color.BLUE; }
    }
}
