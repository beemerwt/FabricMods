package com.github.beemerwt.essence.command;

import com.github.beemerwt.essence.Essence;
import com.github.beemerwt.essence.command.suggest.EnchantSuggester;
import com.github.beemerwt.essence.permission.OpLevel;
import com.github.beemerwt.essence.permission.Permissions;
import com.github.beemerwt.essence.util.CommandOverrideUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EnchantCommand {
    private static final SuggestionProvider<ServerCommandSource> ENCHANT_LEVEL_SUGGESTIONS =
        (ctx, b) -> {
            var rm = Essence.getServer().getRegistryManager();
            var enchantReg = rm.getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
            if (enchantReg == null) return b.buildFuture();

            var enchantId = IdentifierArgumentType.getIdentifier(ctx, "enchantment");
            var selectedEnchant = enchantReg.get(enchantId);
            if (selectedEnchant == null) return b.buildFuture();

            int max = selectedEnchant.getMaxLevel();
            for (int i = 0; i <= max; i++)
                b.suggest(i);

            return b.buildFuture();
        };

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        CommandOverrideUtil.removeLiteral(d, "enchant");
        d.register(
            literal("enchant")
                .requires(src -> Permissions.check(src, "essence.enchant", OpLevel.SUPER_MOD))
                .then(argument("enchantment", IdentifierArgumentType.identifier()).suggests(EnchantSuggester.INSTANCE)
                        .then(argument("level", IntegerArgumentType.integer(0)).suggests(ENCHANT_LEVEL_SUGGESTIONS)
                                .executes(EnchantCommand::setSpecific)))
        );
    }

    private static int setSpecific(CommandContext<ServerCommandSource> ctx) {
        var src = ctx.getSource();
        var player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }

        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            src.sendError(Text.literal("You must hold an enchantable item."));
            return 0;
        }

        var rm = Essence.getServer().getRegistryManager();
        var enchantReg = rm.getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
        if (enchantReg == null) {
            src.sendError(Text.literal("Could not access enchantment registry."));
            return 0;
        }

        var enchantId = IdentifierArgumentType.getIdentifier(ctx, "enchantment");
        var enchantRef = enchantReg.getEntry(enchantId).orElse(null);
        if (enchantRef == null) {
            src.sendError(Text.literal("Enchantment not found."));
            return 0;
        }

        int requested = IntegerArgumentType.getInteger(ctx, "level");
        boolean book = stack.isOf(Items.ENCHANTED_BOOK) || stack.isOf(Items.BOOK);

        // safe because we check isEnchantable
        var builder = new ItemEnchantmentsComponent.Builder(stack.getEnchantments());

        int level = Math.max(0, Math.min(requested, enchantRef.value().getMaxLevel()));
        if (level == 0) {
            builder.remove(entry -> entry.equals(enchantRef));
        } else {
            if (!book && !enchantRef.value().isAcceptableItem(stack)) {
                src.sendError(Text.literal("That enchantment cannot be applied to this item."));
                return 0;
            }

            builder.set(enchantRef, level); // ignore conflicts on purpose
        }

        var result = builder.build();
        if (book) stack.set(DataComponentTypes.STORED_ENCHANTMENTS, result);
        else stack.set(DataComponentTypes.ENCHANTMENTS, result);

        player.currentScreenHandler.sendContentUpdates();
        var name = Enchantment.getName(enchantRef, level);
        if (level == 0) {
            src.sendFeedback(() -> Text.literal("Removed ").append(name)
                    .append(Text.literal(" from " + stack.getName().getString() + ".")), true);
        } else {
            src.sendFeedback(() -> Text.literal("Applied ").append(name)
                    .append(Text.literal(" to " + stack.getName().getString() + ".")), true);
        }

        return Command.SINGLE_SUCCESS;
    }
}
