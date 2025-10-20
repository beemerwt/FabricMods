package com.github.beemerwt.mcrpg.config;

import blue.endless.jankson.Jankson;

import java.nio.file.Path;

public abstract class Config {
    protected static final Jankson J = Jankson.builder().build();
}
