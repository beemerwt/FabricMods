package com.github.beemerwt.commands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stores the current default for each bare literal. */
public final class CommandRouter {
    private static final Map<String, String> BARE_TO_FQN = new ConcurrentHashMap<>();

    private CommandRouter() {}

    /** Set the default mapping for a bare literal (mods calling our literal(...).register()). */
    static void setDefault(String bare, String fqn) {
        BARE_TO_FQN.put(bare, fqn);
    }

    /** Initialize a default only if none exists yet (used to seed vanilla). */
    static void initDefault(String bare, String fqn) {
        BARE_TO_FQN.putIfAbsent(bare, fqn);
    }

    /** If input starts with a bare literal we know, rewrite to its FQN; otherwise return input. */
    public static String rewriteIfBare(String raw) {
        int start = 0;
        while (start < raw.length() && raw.charAt(start) == '/') start++;
        int i = start;
        while (i < raw.length() && !Character.isWhitespace(raw.charAt(i))) i++;
        if (i <= start) return raw; // empty

        String first = raw.substring(start, i);
        if (first.indexOf(':') >= 0) {
            // already namespaced; do not touch
            return raw;
        }

        // sanitize stray leading slashes inside the token
        first = first.replaceAll("^/+", "");
        if (first.isEmpty()) return raw;

        String fqn = BARE_TO_FQN.get(first);
        if (fqn == null) return raw;

        return raw.substring(0, start) + fqn + raw.substring(i);
    }
}

