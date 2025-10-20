package com.github.beemerwt.mcrpg.text;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A minimal, Adventure-like immutable component for literal text.
 * Build with fluent methods, then call toText() to get a Minecraft Text.
 */
public final class Component {
    private final String content;
    private final TextColor color;
    private final boolean bold;
    private final boolean italic;
    private final boolean underlined;
    private final boolean strikethrough;
    private final boolean obfuscated;

    private final List<Component> children;

    private Component(
            String content,
            TextColor color,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            List<Component> children
    ) {
        this.content = content;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.children = children == null ? List.of() : Collections.unmodifiableList(children);
    }

    // ===== Factory methods =====

    public static Component text(String content) {
        return new Component(
                content,
                TextColor.fromRgb(NamedTextColor.WHITE.value()),
                false, false, false, false, false,
                List.of()
        );
    }

    public static Component text(String content, TextColor color) {
        return new Component(
                content,
                color,
                false, false, false, false, false,
                List.of()
        );
    }

    public static Component text(String content, NamedTextColor color) {
        return text(content, TextColor.fromRgb(color.value()));
    }

    public static Component empty() {
        return text("");
    }

    // ===== Fluent "with" style setters (return new instances) =====

    public Component color(TextColor color) {
        return new Component(content, color, bold, italic, underlined, strikethrough, obfuscated, children);
    }

    public Component color(NamedTextColor color) {
        return color(TextColor.fromRgb(color.value()));
    }

    public Component bold(boolean value) {
        return new Component(content, color, value, italic, underlined, strikethrough, obfuscated, children);
    }

    public Component italic(boolean value) {
        return new Component(content, color, bold, value, underlined, strikethrough, obfuscated, children);
    }

    public Component underlined(boolean value) {
        return new Component(content, color, bold, italic, value, strikethrough, obfuscated, children);
    }

    public Component strikethrough(boolean value) {
        return new Component(content, color, bold, italic, underlined, value, obfuscated, children);
    }

    public Component obfuscated(boolean value) {
        return new Component(content, color, bold, italic, underlined, strikethrough, value, children);
    }

    // ===== Children =====

    public Component append(Component child) {
        Objects.requireNonNull(child, "child");
        List<Component> newChildren = new ArrayList<>(this.children.size() + 1);
        newChildren.addAll(this.children);
        newChildren.add(child);
        return new Component(content, color, bold, italic, underlined, strikethrough, obfuscated, newChildren);
    }

    public Component appendAll(List<Component> more) {
        if (more == null || more.isEmpty()) return this;
        List<Component> newChildren = new ArrayList<>(this.children.size() + more.size());
        newChildren.addAll(this.children);
        newChildren.addAll(more);
        return new Component(content, color, bold, italic, underlined, strikethrough, obfuscated, newChildren);
    }

    public List<Component> children() {
        return this.children;
    }

    public String content() {
        return this.content;
    }

    // ===== Render to Minecraft Text =====

    public Text toText() {
        // Build the base literal
        var base = Text.literal(content);

        // Apply style all at once (avoids multiple lambdas)
        Style styled = Style.EMPTY
                .withColor(this.color)
                .withBold(this.bold)
                .withItalic(this.italic)
                .withUnderline(this.underlined)
                .withStrikethrough(this.strikethrough)
                .withObfuscated(this.obfuscated);

        base.setStyle(styled);

        // Recursively append children
        for (Component child : this.children) {
            base = base.copy().append(child.toText());
        }

        return base;
    }

    // ===== Convenience short-hands (Adventure-like) =====

    public Component color(int rgb) {
        return color(TextColor.fromRgb(rgb));
    }

    public Component bold() { return bold(true); }
    public Component italic() { return italic(true); }
    public Component underlined() { return underlined(true); }
    public Component strikethrough() { return strikethrough(true); }
    public Component obfuscated() { return obfuscated(true); }
}
