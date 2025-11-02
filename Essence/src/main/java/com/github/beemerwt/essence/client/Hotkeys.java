package com.github.beemerwt.essence.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class Hotkeys {
    /** Category shown in Controls; localize with key.category.essence.general -> "Essence" */
    public static final KeyBinding.Category ESSENCE_GENERAL =
        KeyBinding.Category.create(Identifier.of("essence", "general"));

    private static final Map<String, Hotkey> HOTKEYS = new LinkedHashMap<>();
    private static boolean tickRegistered = false;

    private Hotkeys() {}

    /**
     * Register the per-tick updater. Call once from your client entrypoint.
     */
    public static void register() {
        if (tickRegistered) return;
        tickRegistered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Update all hotkeys once per client tick
            for (Hotkey hk : HOTKEYS.values()) {
                hk.update(client);
            }
        });
    }

    /**
     * Registers a new keybinding under {@link #ESSENCE_GENERAL}.
     *
     * @param translationKey e.g. "key.essence.noclip" (localize in assets/essence/lang/en_us.json)
     * @param defaultKey GLFW keycode, e.g. {@link GLFW#GLFW_KEY_G}
     * @return a {@link Hotkey} wrapper exposing edge-aware helpers
     */
    public static Hotkey registerHotkey(KeyBinding.Category cat, String translationKey, int defaultKey) {
        KeyBinding binding = new KeyBinding(translationKey, InputUtil.Type.KEYSYM, defaultKey, cat);
        KeyBindingHelper.registerKeyBinding(binding);

        Hotkey hk = new Hotkey(translationKey, binding);
        HOTKEYS.put(translationKey, hk);
        return hk;
    }

    @Environment(EnvType.CLIENT)
    public static final class Hotkey {
        private final String key;
        private final KeyBinding binding;

        // Snapshotted each tick by update()
        private boolean prevDown = false;
        private boolean isDown = false;
        private boolean pressedThisTick = false; // drained from KeyBinding.wasPressed()
        private boolean edgePressed = false;     // true exactly on rising edge (debounced)

        private Consumer<MinecraftClient> onPressedCallback;

        private Hotkey(String key, KeyBinding binding) {
            this.key = key;
            this.binding = binding;
        }

        private void update(MinecraftClient client) {
            // 1) Drain queued press events so they don't linger to next tick
            pressedThisTick = false;
            while (binding.wasPressed()) {
                pressedThisTick = true;
            }

            // 2) Rising-edge detection (requires release before firing again)
            prevDown = isDown;
            isDown = binding.isPressed();
            edgePressed = isDown && !prevDown;

            // Run callback once per tick if queued presses occurred
            if (pressedThisTick && onPressedCallback != null) {
                onPressedCallback.accept(client);
            }
        }

        /** Physical state right now (key is currently held). */
        public boolean isPressed() {
            return isDown;
        }

        /**
         * True exactly once when the key transitions from up->down (debounced).
         * Ideal for toggles (noclip, flashlight, etc.).
         */
        public boolean wasPressed() {
            return edgePressed;
        }

        /**
         * True if Fabric queued any press events this tick (after draining).
         * Useful when you want to react to all repeats, but still handle debouncing yourself.
         */
        public boolean wasPressedThisTick() {
            return pressedThisTick;
        }

        /** Set a callback that runs every tick the key was pressed (after debouncing drain). */
        public Hotkey onKeyPressedThisTick(Consumer<MinecraftClient> callback) {
            this.onPressedCallback = callback;
            return this;
        }

        public String id() {
            return key;
        }

        public KeyBinding vanilla() {
            return binding;
        }
    }
}
