package com.github.beemerwt.commands;

final class Rewrite {
    private Rewrite() {}

    /** Replace the very first token with `toToken`, preserving leading slashes and spacing. */
    static String firstTokenTo(String input, String fromToken, String toToken) {
        int start = 0;
        while (start < input.length() && input.charAt(start) == '/') start++;
        int i = start;
        while (i < input.length() && !Character.isWhitespace(input.charAt(i))) i++;
        if (!input.substring(start, i).equals(fromToken)) return input; // safety
        return input.substring(0, start) + toToken + input.substring(i);
    }
}
