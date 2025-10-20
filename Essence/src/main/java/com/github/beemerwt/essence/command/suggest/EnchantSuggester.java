package com.github.beemerwt.essence.command.suggest;

import com.github.beemerwt.essence.Essence;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;

import java.util.concurrent.CompletableFuture;

public final class EnchantSuggester implements SuggestionProvider<ServerCommandSource> {
    public static final EnchantSuggester INSTANCE = new EnchantSuggester();

    private EnchantSuggester() {}

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder b) {
        var rm = Essence.getServer().getRegistryManager();
        var enchantReg = rm.getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
        if (enchantReg == null) {
            Essence.getLogger().debug("Could not get enchantment registry for suggestions");
            return b.buildFuture();
        }

        var player = ctx.getSource().getPlayer();
        if (player == null) {
            Essence.getLogger().debug("Could not get player for enchantment suggestions");
            return b.buildFuture();
        }

        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            Essence.getLogger().debug("Item in hand is empty or not enchantable");
            return b.buildFuture();
        }

        enchantReg.stream().forEach(entry -> {
            var applicable = entry.isAcceptableItem(stack);
            if (applicable) {
                Essence.getLogger().debug("Enchantment {} is applicable to {}", enchantReg.getId(entry), stack.getName().getString());
            }
        });

        boolean isBook = stack.isOf(Items.ENCHANTED_BOOK) || stack.isOf(Items.BOOK);
        enchantReg.stream().forEach(entry -> {
            var applicable = entry.isAcceptableItem(stack);
            var id = enchantReg.getId(entry);
            if (id != null && (applicable || isBook))
                b.suggest(id.toString());
        });

        return b.buildFuture();
    }
}
