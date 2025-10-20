package com.github.beemerwt.util;

import com.mojang.logging.LogUtils;
import org.apache.logging.log4j.Level;
import org.slf4j.Logger;

import java.text.MessageFormat;
import java.util.MissingFormatArgumentException;


/**
 * FabricLogger is a thin SLF4J-style wrapper around java.util.logging.Logger.
 * Supports {}-style placeholders and provides standard levels.
 *
 * Example:
 *   FabricLogger LOG = FabricLogger.get("MyMod");
 *   LOG.info("Loaded {} configs from {}", count, path);
 *   LOG.debug("Player {} joined world {}", player, world);
 */
public class FabricLogger {
    private final Logger logger;
    private static boolean globalDebug = false;
    private static String PREFIX = "";

    /** Creates a logger with a given name (usually your mod ID). */
    public static FabricLogger getLogger(String name) {
        Logger parent = LogUtils.getLogger();
        Logger child = org.slf4j.LoggerFactory.getLogger(name);
        PREFIX = "[" + name + "] ";
        return new FabricLogger(child != null ? child : parent);
    }

    /** Enables or disables global debug output. */
    public static void setGlobalDebug(boolean enabled) {
        globalDebug = enabled;
    }

    private FabricLogger(Logger logger) {
        this.logger = logger;
    }

    // ---------------- SLF4J-style helpers ----------------
    private String prefix(String msg) {
        return PREFIX + msg;
    }

    public void debug(String msg, Object... args) {
        if (globalDebug) logger.info(prefix(format(msg, args)));
    }

    public void info(String msg, Object... args) {
        logger.info(prefix(format(msg, args)));
    }

    public void warning(String msg, Object... args) {
        logger.warn(prefix(format(msg, args)));
    }

    public void error(String msg, Object... args) {
        logger.error(prefix(format(msg, args)));
    }

    public void severe(String msg, Object... args) {
        logger.warn(prefix(format(msg, args)));
    }

    // ------------- Internal formatting helpers -------------

    private static String format(String pattern, Object... args) {
        if (pattern == null) return "null";
        if (args == null || args.length == 0) return pattern;

        // Lightweight "{}" formatter
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int cursor = 0;
        while (true) {
            int brace = pattern.indexOf("{}", cursor);
            if (brace == -1) {
                sb.append(pattern, cursor, pattern.length());
                break;
            }
            sb.append(pattern, cursor, brace);
            if (argIndex < args.length) {
                sb.append(String.valueOf(args[argIndex++]));
            } else {
                sb.append("{}"); // unmatched
            }
            cursor = brace + 2;
        }
        // If there are extra args, append them
        if (argIndex < args.length) {
            sb.append(" [");
            for (int i = argIndex; i < args.length; i++) {
                sb.append(String.valueOf(args[i]));
                if (i < args.length - 1) sb.append(", ");
            }
            sb.append(']');
        }
        return sb.toString();
    }

    /** Shortcut: log exception with message (auto formats). */
    public void error(Throwable t, String msg, Object... args) {
        logger.error(prefix(format(msg, args)), t);
    }

    /** Shortcut: log exception with message (debug level). */
    public void debug(Throwable t, String msg, Object... args) {
        if (globalDebug) logger.info(prefix(format(msg, args)), t);
    }
}

