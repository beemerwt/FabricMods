package com.github.beemerwt.mcrpg.data;

import com.github.beemerwt.mcrpg.util.TextUtil;

import java.util.Optional;

public enum SkillType {
    // General skills
    MINING((short) 0),
    WOODCUTTING((short) 1),
    EXCAVATION((short) 2),
    ACROBATICS((short) 3),
    HERBALISM((short) 8),

    // Artisan Skills
    SMELTING((short) 9),
    REPAIR((short) 10),
    SALVAGE((short) 11),

    // Combat-specific
    SWORDS((short) 4),
    ARCHERY((short) 5),
    AXES((short) 6),
    UNARMED((short) 7);

    private final short id;
    SkillType(short id) {
        this.id = id;
    }

    public static SkillType fromId(short id) {
        for (SkillType st : values()) {
            if (st.id == id) return st;
        }

        throw new IllegalArgumentException("No SkillType with id " + id);
    }

    public static Optional<SkillType> parseSkill(String name) {
        try {
            return Optional.of(SkillType.valueOf(name.trim().toUpperCase()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public short id() { return id; }
    public String getName() { return TextUtil.toTitleCase(this.name()); }
}
