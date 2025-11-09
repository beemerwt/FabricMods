package com.github.beemerwt.mcrpg.ability;

import com.github.beemerwt.mcrpg.config.TreasureEntry;
import com.github.beemerwt.mcrpg.config.ability.TreasureFindingConfig;
import com.github.beemerwt.mcrpg.config.skills.ExcavationConfig;
import com.github.beemerwt.mcrpg.data.Leveling;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.event.BreakBlockEvent;
import com.github.beemerwt.mcrpg.service.XpModifier;
import com.github.beemerwt.mcrpg.util.EventBus;
import com.github.beemerwt.mcrpg.util.ItemClassifier;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

public class TreasureFinding extends PassiveAbility<ExcavationConfig, TreasureFindingConfig> {

    static final class CompiledTreasure {
        final String id;
        final int levelReq;
        final int amount;
        final long xp;
        final double p;       // configured probability in [0,1]
        final double lambda;  // -ln(1 - p), precomputed

        CompiledTreasure(String id, TreasureEntry e, double p) {
            this.id = id;
            this.levelReq = e.levelRequirement;
            this.amount = e.amount;
            this.xp = e.xp;
            this.p = p;
            this.lambda = pToLambdaProb(this.p);
        }
    }

    static final class BlockIndex {
        final CompiledTreasure[] all; // sorted by levelReq asc

        BlockIndex(List<CompiledTreasure> entries) {
            this.all = entries.toArray(CompiledTreasure[]::new);
            Arrays.sort(this.all, Comparator.comparingInt(t -> t.levelReq));
        }

        // count of entries with levelReq <= level
        static int activeCount(CompiledTreasure[] arr, int level) {
            int lo = 0, hi = arr.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (arr[mid].levelReq <= level) lo = mid + 1; else hi = mid;
            }
            return lo;
        }
    }

    private volatile Map<String, BlockIndex> compiledByBlock = Map.of(); // swap atomically on rebuild

    public TreasureFinding(Supplier<TreasureFindingConfig> aConfigSupplier) {
        super(SkillType.EXCAVATION, aConfigSupplier);
        rebuildTreasureIndex();

        EventBus.on(BreakBlockEvent.class, this::onBlockBreak);
    }

    @Override protected String id() { return "mcrpg.ability.treasure_finding"; }

    private void onBlockBreak(BreakBlockEvent e) {
        if (!config.enabled) return;
        if (!ItemClassifier.isShovel(e.tool().getItem())) return;

        final BlockIndex idx = indexForBlock(Registries.BLOCK.getId(e.block()));
        if (idx == null) return;

        final var rng = e.player().getRandom();
        final int level = Leveling.getLevel(e.player(), SkillType.EXCAVATION);

        final CompiledTreasure[] arr = idx.all;
        final int n = BlockIndex.activeCount(arr, level);
        if (n <= 0) return;

        // Sum lambdas for all active entries (skip zeros)
        double sumLambda = 0.0;
        int pick = -1;
        for (int i = 0; i < n; i++) {
            final double li = arr[i].lambda;
            if (li <= 0.0) continue;
            final double newSum = sumLambda + li;
            // Weighted reservoir: select i with prob li/newSum
            if (rng.nextDouble() < (li / newSum)) pick = i;
            sumLambda = newSum;
        }

        if (sumLambda <= 0.0) return;                 // all p==0 after gating
        if (rng.nextDouble() < Math.exp(-sumLambda)) return; // "no drop" with prob ∏(1-p_i)

        // Conditional on a drop, pick ~ lambda_i / sumLambda
        if (pick >= 0) {
            final CompiledTreasure t = arr[pick];
            spawnItem(e.world(), e.pos(), t.id, t.amount);
            final double xp = t.xp * skillConfig.xpModifier;
            XpModifier.addFlat(e.player(), SkillType.EXCAVATION, xp);
        }
    }

    private static double getChance(TreasureEntry e) {
        double p = e.dropChance * 0.01;
        if (p <= 0.0) return 0.0;
        return Math.min(p, 1.0);
    }

    private static double pToLambdaProb(double p) {
        // p in [0,1). lambda = -ln(1 - p). Exact mapping for independent-per-item model.
        return (p > 0.0) ? -Math.log1p(-p) : 0.0;
    }

    /** Utility to look up an index; returns null if block has no treasure entries. */
    @Nullable
    private BlockIndex indexForBlock(Identifier blockId) {
        return compiledByBlock.get(blockId.toString());
    }

    private static void spawnItem(World world, BlockPos pos, String itemId, int amount) {
        var item = Registries.ITEM.get(Identifier.tryParse(itemId));
        if (item == Items.AIR) return; // invalid item

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        var stack = new ItemStack(item, amount);
        var entity = new ItemEntity(world, cx, cy, cz, stack);
        world.spawnEntity(entity);
    }

    /** Call this when the excavation config reloads. */
    public void rebuildTreasureIndex() {
        if (config.treasures == null || config.treasures.isEmpty()) {
            compiledByBlock = Map.of();
            return;
        }

        final Map<String, List<CompiledTreasure>> byBlock = new HashMap<>();

        for (Map.Entry<String, TreasureEntry> me : config.treasures.entrySet()) {
            final String id = me.getKey();
            final TreasureEntry e = me.getValue();

            // Require explicit dropsFrom; ignore empty/none
            if (e.dropsFrom == null || e.dropsFrom.isEmpty()) continue;

            final double p = getChance(e); // interpret as probability
            if (p <= 0.0) continue;

            final CompiledTreasure ct = new CompiledTreasure(id, e, p);

            // If p==0 after clamp, it will be skipped in hot path anyway
            for (String blockKey : e.dropsFrom) {
                byBlock.computeIfAbsent(blockKey, k -> new ArrayList<>()).add(ct);
            }
        }

        if (byBlock.isEmpty()) {
            compiledByBlock = Map.of();
            return;
        }

        final Map<String, BlockIndex> out = new HashMap<>(byBlock.size());
        for (Map.Entry<String, List<CompiledTreasure>> en : byBlock.entrySet()) {
            out.put(en.getKey(), new BlockIndex(en.getValue()));
        }

        compiledByBlock = Collections.unmodifiableMap(out);
    }

    @Override
    public void onReload(ExcavationConfig config) {
        super.onReload(config);
        rebuildTreasureIndex();
    }
}
