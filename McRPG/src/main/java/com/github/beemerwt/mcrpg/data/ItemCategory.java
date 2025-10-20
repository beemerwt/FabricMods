package com.github.beemerwt.mcrpg.data;

public enum ItemCategory {
    WOOD("wood"),
    STONE("stone"),
    COPPER("copper"),
    IRON("iron"),
    GOLD("gold"),
    DIAMOND("diamond"),
    NETHERITE("netherite"),
    STRING("string"),
    LEATHER("leather"),
    OTHER("other");

    public final String category;
    ItemCategory(String category) {
        this.category = category;
    }
}
