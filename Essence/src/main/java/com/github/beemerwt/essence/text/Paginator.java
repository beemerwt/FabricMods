package com.github.beemerwt.essence.text;

import net.minecraft.dialog.Dialogs;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Simple helper to create consistent page headers and footers
 * for list-style commands (e.g. /ban list, /jail list, etc.)
 */
public final class Paginator {

    private Paginator() {}

    /**
     * Generates a standard header with optional clickable page arrows.
     * Example:
     *   [«]  Active Bans  (page 1 / 4)  [»]
     *
     * @param title     The title of the listing ("Active Bans")
     * @param page      Current page number
     * @param totalPages Total pages
     * @param baseCmd   Command root, e.g. "/ban list" (used for arrows)
     */
    public static Text header(String title, int page, int totalPages, String baseCmd) {
        MutableText text = Text.empty();

        // « Prev
        if (page > 1) {
            text.append(makeArrow("«", baseCmd + " " + (page - 1), "Go to page " + (page - 1)));
            text.append(Text.literal(" "));
        } else {
            text.append(Text.literal("« ").formatted(Formatting.DARK_GRAY));
        }

        // Title + page counter
        text.append(Text.literal(" " + title + " ").formatted(Formatting.GOLD));
        text.append(Text.literal("(page " + page + " / " + totalPages + ")").formatted(Formatting.GRAY));
        text.append(Text.literal(" "));

        // Next »
        if (page < totalPages) {
            text.append(makeArrow("»", baseCmd + " " + (page + 1), "Go to page " + (page + 1)));
        } else {
            text.append(Text.literal(" »").formatted(Formatting.DARK_GRAY));
        }

        return text;
    }

    private static MutableText makeArrow(String symbol, String cmd, String hover) {
        return Text.literal(symbol)
            .formatted(Formatting.AQUA)
            .styled(s -> s
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(hover)))
            );
    }

    /**
     * Optional helper for footers (useful when you want to repeat page controls at the bottom)
     */
    public static Text footer(int page, int totalPages, String baseCmd) {
        return header("", page, totalPages, baseCmd);
    }
}

