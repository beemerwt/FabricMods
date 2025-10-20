package com.github.beemerwt.mcrpg.text;

import net.minecraft.text.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record NamedTextColor(String name, int value) {
    private static final int BLACK_VALUE = 0x000000;
    private static final int DARK_BLUE_VALUE = 0x0000aa;
    private static final int DARK_GREEN_VALUE = 0x00aa00;
    private static final int DARK_AQUA_VALUE = 0x00aaaa;
    private static final int DARK_RED_VALUE = 0xaa0000;
    private static final int DARK_PURPLE_VALUE = 0xaa00aa;
    private static final int GOLD_VALUE = 0xffaa00;
    private static final int GRAY_VALUE = 0xaaaaaa;
    private static final int DARK_GRAY_VALUE = 0x555555;
    private static final int BLUE_VALUE = 0x5555ff;
    private static final int GREEN_VALUE = 0x55ff55;
    private static final int AQUA_VALUE = 0x55ffff;
    private static final int RED_VALUE = 0xff5555;
    private static final int LIGHT_PURPLE_VALUE = 0xff55ff;
    private static final int YELLOW_VALUE = 0xffff55;
    private static final int WHITE_VALUE = 0xffffff;

    public static final Map<String, NamedTextColor> NAMES = new HashMap<>();
    private static final List<NamedTextColor> VALUES = new ArrayList<>();

    /**
     * The standard {@code black} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor BLACK = new NamedTextColor("black", BLACK_VALUE);
    /**
     * The standard {@code dark_blue} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor DARK_BLUE = new NamedTextColor("dark_blue", DARK_BLUE_VALUE);
    /**
     * The standard {@code dark_green} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor DARK_GREEN = new NamedTextColor("dark_green", DARK_GREEN_VALUE);
    /**
     * The standard {@code dark_aqua} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor DARK_AQUA = new NamedTextColor("dark_aqua", DARK_AQUA_VALUE);
    /**
     * The standard {@code dark_red} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor DARK_RED = new NamedTextColor("dark_red", DARK_RED_VALUE);
    /**
     * The standard {@code dark_purple} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor DARK_PURPLE = new NamedTextColor("dark_purple", DARK_PURPLE_VALUE);
    /**
     * The standard {@code gold} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor GOLD = new NamedTextColor("gold", GOLD_VALUE);
    /**
     * The standard {@code gray} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor GRAY = new NamedTextColor("gray", GRAY_VALUE);
    /**
     * The standard {@code dark_gray} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor DARK_GRAY = new NamedTextColor("dark_gray", DARK_GRAY_VALUE);
    /**
     * The standard {@code blue} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor BLUE = new NamedTextColor("blue", BLUE_VALUE);
    /**
     * The standard {@code green} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor GREEN = new NamedTextColor("green", GREEN_VALUE);
    /**
     * The standard {@code aqua} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor AQUA = new NamedTextColor("aqua", AQUA_VALUE);
    /**
     * The standard {@code red} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor RED = new NamedTextColor("red", RED_VALUE);
    /**
     * The standard {@code light_purple} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor LIGHT_PURPLE = new NamedTextColor("light_purple", LIGHT_PURPLE_VALUE);
    /**
     * The standard {@code yellow} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor YELLOW = new NamedTextColor("yellow", YELLOW_VALUE);
    /**
     * The standard {@code white} colour.
     *
     * @since 4.0.0
     */
    public static final NamedTextColor WHITE = new NamedTextColor("white", WHITE_VALUE);

    /**
     * Gets the named color exactly matching the provided color.
     *
     * @param value the color to match
     * @return the matched color, or null
     * @since 4.10.0
     */
    public static @Nullable NamedTextColor namedColor(final int value) {
        switch (value) {
            case BLACK_VALUE:
                return BLACK;
            case DARK_BLUE_VALUE:
                return DARK_BLUE;
            case DARK_GREEN_VALUE:
                return DARK_GREEN;
            case DARK_AQUA_VALUE:
                return DARK_AQUA;
            case DARK_RED_VALUE:
                return DARK_RED;
            case DARK_PURPLE_VALUE:
                return DARK_PURPLE;
            case GOLD_VALUE:
                return GOLD;
            case GRAY_VALUE:
                return GRAY;
            case DARK_GRAY_VALUE:
                return DARK_GRAY;
            case BLUE_VALUE:
                return BLUE;
            case GREEN_VALUE:
                return GREEN;
            case AQUA_VALUE:
                return AQUA;
            case RED_VALUE:
                return RED;
            case LIGHT_PURPLE_VALUE:
                return LIGHT_PURPLE;
            case YELLOW_VALUE:
                return YELLOW;
            case WHITE_VALUE:
                return WHITE;
            default:
                return null;
        }
    }

    public NamedTextColor(final String name, final int value) {
        this.name = name;
        this.value = value;

        VALUES.add(this);
        NAMES.put(name, this);
    }

    public TextColor asTextColor() {
        return TextColor.fromRgb(this.value);
    }

    @Override
    public @NotNull String toString() {
        return this.name;
    }
}

