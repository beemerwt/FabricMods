package com.github.beemerwt.mcrpg.util;

import java.util.Locale;

public class TextUtil {
    public static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;

        String[] words = input.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(' ');
            }
        }

        return sb.toString().trim();
    }
}
