package com.github.beemerwt.mcrpg.data;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;

public final class SkillLinks {

    private SkillLinks() {}

    // Simple alias (use target as the canonical bucket)
    private static final EnumMap<SkillType, SkillType> ALIAS = new EnumMap<>(SkillType.class);

    /**
     * @param weights should sum to 1.0
     */ // Composite: skill derived from other skills (by level), with weights for XP routing
    public record Composite(SkillType[] parts, double[] weights) {
        public Composite(SkillType[] parts, double[] weights) {
            this.parts = Objects.requireNonNull(parts);
            this.weights = Objects.requireNonNull(weights);
            if (parts.length == 0 || parts.length != weights.length) {
                throw new IllegalArgumentException("parts/weights mismatch or empty");
            }
            double sum = 0.0;
            for (double w : weights) sum += w;
            if (sum <= 0.0) throw new IllegalArgumentException("weights must sum > 0");
            // normalize
            for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        }

        public static Composite average(SkillType... parts) {
            double[] w = new double[parts.length];
            Arrays.fill(w, 1.0 / parts.length);
            return new Composite(parts, w);
        }
    }

    private static final EnumMap<SkillType, Composite> COMPOSITE = new EnumMap<>(SkillType.class);

    static {
        // Sister skill: Salvage writes/reads Repair
        ALIAS.put(SkillType.SALVAGE, SkillType.REPAIR);

        // Derived skill: Smelting level = average(Repair, Mining)
        // XP given to Smelting is split 50/50 to Repair/Mining
        COMPOSITE.put(SkillType.SMELTING, Composite.average(SkillType.REPAIR, SkillType.MINING));
    }

    public static boolean isAlias(SkillType s)     { return ALIAS.containsKey(s); }
    public static boolean isComposite(SkillType s) { return COMPOSITE.containsKey(s); }

    public static SkillType primaryOf(SkillType s) { return ALIAS.getOrDefault(s, s); }

    public static Composite compositeOf(SkillType s) { return COMPOSITE.get(s); }
}
