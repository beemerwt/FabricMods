package com.github.beemerwt.mcrpg.config;

import com.github.beemerwt.mcrpg.annotation.JankComment;
import com.github.beemerwt.mcrpg.annotation.JanksonObject;

@JanksonObject
public class AbilityConfig {
    @JankComment("Set to false to disable this ability")
    public boolean enabled = true;
}
