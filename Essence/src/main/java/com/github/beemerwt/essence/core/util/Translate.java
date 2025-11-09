package com.github.beemerwt.essence.core.util;

import net.minecraft.text.Text;

public class Translate {

    public static Text literal(String translationKey, Object... args) {
        var textElem = Text.translatable(translationKey, args);
        String literal = textElem.getString();
        return Text.literal(literal);
    }
}
