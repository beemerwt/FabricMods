package com.github.beemerwt.essence.core.command;

import com.github.beemerwt.essence.core.Essence;
import com.github.beemerwt.essence.core.permission.Perms;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemPredicateArgumentType;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static com.github.beemerwt.essence.core.command.LiteralShim.commandLiteral;

public final class FindItemCommand {
    // Optional: wire to your own permission gate.

    public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess access) {
        d.register(commandLiteral("essence", "finditem").requires(Perms.FIND_ITEM)
                .then(argument("item", ItemPredicateArgumentType.itemPredicate(access))
                    .executes(ctx -> run(ctx, 32)) // default radius
                    .then(argument("radius", IntegerArgumentType.integer(1, 256))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                    )
                )
        );
    }

    private static int run(CommandContext<ServerCommandSource> ctx, int radius) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        ServerWorld world = player.getEntityWorld();
        BlockPos center = player.getBlockPos();

        ItemPredicateArgumentType.ItemStackPredicateArgument itemPredicate = null;
        try {
            itemPredicate = ItemPredicateArgumentType.getItemStackPredicate(ctx, "item");
        } catch (Exception e) {
            src.sendError(Text.literal("Invalid item predicate."));
            Essence.getLogger().warn(e, "Error parsing item predicate in /finditem command");
            return 0;
        }

        String query = "";
        try {
            query = getRawArgument(ctx, "item");
        } catch (IllegalArgumentException e) {
            Essence.getLogger().warn(e, "Error getting item predicate string in /finditem command");
        }

        // TODO: Handle this off the main thread.

        List<Result> results = Collections.emptyList();
        try {
            // Scan containers within radius, chunk-by-chunk
            results = scanForItem(world, center, radius, itemPredicate);
        } catch (Exception e) {
            src.sendError(Text.literal("An error occurred while scanning for items."));
            Essence.getLogger().error(e, "Error scanning for items in /finditem command");
            return 0;
        }

        // Header
        MutableText header = Text.literal("Find ")
            .append(Text.literal(query).formatted(Formatting.GOLD))
            .append(Text.literal(" within " + radius + " blocks"))
            .formatted(Formatting.YELLOW);
        src.sendFeedback(() -> header, false);

        if (results.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No matches found.").formatted(Formatting.GRAY), false);
            return 1;
        }

        // Sort by distance ascending
        results.sort(Comparator.comparingInt((Result r) -> r.manhattan(center)));

        int shown = Math.min(results.size(), 20);
        for (int i = 0; i < shown; i++) {
            var r = results.get(i);
            String label = r.containerType + " @ " + r.pos.getX() + " " + r.pos.getY() + " " + r.pos.getZ();
            String invSeeCmd = "/invsee " + r.pos.getX() + " " + r.pos.getY() + " " + r.pos.getZ();
            String highlightCmd = "/highlight " + r.pos.getX() + " " + r.pos.getY() + " " + r.pos.getZ();

            MutableText line = Text.literal("• ")
                .formatted(Formatting.DARK_GRAY)
                .append(Text.literal(label + "  ").styled(style -> {
                    if (Perms.INV_SEE.check(src))
                        style = style.withClickEvent(new ClickEvent.RunCommand(invSeeCmd));
                    return style.withColor(Formatting.AQUA);
                }))
                .append(Text.literal("(" + r.manhattan(center) + " blocks away)").styled(style -> {
                    if (Perms.HIGHLIGHT.check(src))
                        style = style.withClickEvent(new ClickEvent.RunCommand(highlightCmd)).withUnderline(true);
                    return style.withColor(Formatting.DARK_GRAY);
                }));

            src.sendFeedback(() -> line, false);
        }

        if (results.size() > shown) {
            final int resultSize = results.size();
            src.sendFeedback(() -> Text.literal("…and " + (resultSize - shown) + " more.")
                .formatted(Formatting.GRAY), false);
        }

        return 1;
    }

    static String getRawArgument(CommandContext<?> ctx, String name) {
        var range = ctx.getNodes().stream()
            .filter(n -> n.getNode().getName().equals(name))
            .findFirst()
            .map(ParsedCommandNode::getRange)
            .orElse(null);
        return range == null ? "" : ctx.getInput().substring(range.getStart(), range.getEnd());
    }

    private record Result(BlockPos pos, String containerType, List<ItemStack> items) {
        int manhattan(BlockPos origin) {
            return Math.abs(pos.getX() - origin.getX()) + Math.abs(pos.getY() - origin.getY()) + Math.abs(pos.getZ() - origin.getZ());
        }
    }

    private static List<Result> scanForItem(
        ServerWorld world, BlockPos center, int radius, ItemPredicateArgumentType.ItemStackPredicateArgument pred
    ) {
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(world.getBottomY(), center.getY() - radius);
        int maxY = Math.min(world.getLogicalHeight() - 1, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        int minChunkX = MathHelper.floorDiv(minX, 16);
        int maxChunkX = MathHelper.floorDiv(maxX, 16);
        int minChunkZ = MathHelper.floorDiv(minZ, 16);
        int maxChunkZ = MathHelper.floorDiv(maxZ, 16);

        List<Result> out = new ArrayList<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                var chunk = world.getChunk(cx, cz);
                // Iterate *loaded* block entities in this chunk
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    if (pos.getX() < minX || pos.getX() > maxX ||
                        pos.getY() < minY || pos.getY() > maxY ||
                        pos.getZ() < minZ || pos.getZ() > maxZ) {
                        continue;
                    }

                    BlockState state = world.getBlockState(pos);

                    // If it's a chest half, only process one side to avoid duplicates.
                    if (state.getBlock() instanceof ChestBlock) {
                        var type = state.get(ChestBlock.CHEST_TYPE);
                        if (type == ChestType.RIGHT) {
                            // We'll handle the pair from the LEFT (or SINGLE) side.
                            continue;
                        }
                    }

                    // Prefer merged chest inventory if this is a chest half
                    Inventory inv = tryGetUnifiedInventory(world, pos);
                    String containerName;

                    if (inv != null) {
                        containerName = getContainerTypeName(world.getBlockState(pos));
                    } else if (be instanceof Inventory direct) {
                        inv = direct;
                        containerName = getContainerTypeName(world.getBlockState(pos));
                    } else {
                        // Not a vanilla inventory (e.g., furnaces, barrels, chests, hoppers, shulkers are covered above).
                        // If you want to cover mods or new vanilla storages, consider Fabric Transfer API here.
                        continue;
                    }

                    if (!inv.containsAny(pred))
                        continue;

                    List<ItemStack> items = new ArrayList<>();
                    for (ItemStack item : inv) {
                        if (pred.test(item)) {
                            items.add(item.copy());
                        }
                    }

                    out.add(new Result(pos, containerName, items));
                }
            }
        }

        return out;
    }

    /**
     * If the block at pos is a chest, return a unified inventory (double chest aware).
     * Otherwise returns null.
     */
    private static Inventory tryGetUnifiedInventory(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock block)) return null;
        // Returns single or double chest view depending on adjacency.
        return ChestBlock.getInventory(block, state, world, pos, true);
    }

    private static int countItem(Inventory inv, Item target) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == target) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static String getContainerTypeName(BlockState state) {
        // Simple readable name; tweak as you like
        String id = state.getBlock().getName().getString();
        // Capitalize-ish fallback
        if (id.isEmpty()) return "Container";
        return id;
    }
}
