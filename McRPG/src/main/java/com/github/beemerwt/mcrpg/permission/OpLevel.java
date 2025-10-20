package com.github.beemerwt.mcrpg.permission;

public enum OpLevel {
    NONE(0),
    MODERATOR(1),
    SUPER_MOD(2),
    ADMIN(3),
    OWNER(4);

    final int level;
    OpLevel(int level) { this.level = level; }
}
