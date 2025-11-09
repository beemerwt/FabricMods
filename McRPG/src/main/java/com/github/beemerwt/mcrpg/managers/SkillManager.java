package com.github.beemerwt.mcrpg.managers;

import com.github.beemerwt.mcrpg.config.SkillConfig;
import com.github.beemerwt.mcrpg.data.SkillType;
import com.github.beemerwt.mcrpg.skills.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;

import java.util.EnumMap;
import java.util.Optional;

public class SkillManager {
    private static final EnumMap<SkillType, Skill<?>> skills = new EnumMap<>(SkillType.class);

    private SkillManager() { }

    public static void init() {
        skills.put(SkillType.ACROBATICS, new Acrobatics());
        skills.put(SkillType.EXCAVATION, new Excavation());
        skills.put(SkillType.HERBALISM, new Herbalism());
        skills.put(SkillType.MINING, new Mining());
        skills.put(SkillType.REPAIR, new Repair());
        skills.put(SkillType.SALVAGE, new Salvage());
        skills.put(SkillType.SMELTING, new Smelting());
        skills.put(SkillType.SWORDS, new Swords());
        skills.put(SkillType.UNARMED, new Unarmed());
        skills.put(SkillType.WOODCUTTING, new Woodcutting());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var skill : skills.values()) {
                skill.tick(server);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static <C extends SkillConfig> Skill<C> getSkill(SkillType skillType) {
        return (Skill<C>)skills.get(skillType);
    }
}
