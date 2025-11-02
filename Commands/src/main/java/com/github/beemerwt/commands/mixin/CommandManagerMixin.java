package com.github.beemerwt.commands.mixin;

import com.github.beemerwt.commands.CommandRouter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rewrites only *bare* commands (no colon in first token) to the current default ns:name
 * right before Brigadier parses them. Explicit ns:name is never changed.
 */
@Mixin(CommandManager.class)
public class CommandManagerMixin {
    /**
     * Target the local variable that holds the input string passed to the dispatcher.
     * Yarn name is execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/lang/String;)I
     * We modify the "input" parameter before it’s parsed.
     */
    @Inject(
        method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void commands$rewriteBareToNamespaced(ParseResults results, String command, CallbackInfo ci) {
        // Fast path: nothing to do
        if (command == null || command.isEmpty()) return;

        String rewritten = CommandRouter.rewriteIfBare(command);
        if (rewritten.equals(command)) return; // not bare or no mapping

        Object source = results.getContext().getSource();
        if (!(source instanceof ServerCommandSource serverSource)) return;

        // Re-parse with the same source, then tail-call CommandManager#execute with corrected pair.
        CommandDispatcher<ServerCommandSource> dispatcher = ((CommandManager)(Object)this).getDispatcher();
        ParseResults<ServerCommandSource> newResults = dispatcher.parse(rewritten, serverSource);

        // Important: recursive call is safe because the first token now has a colon, so rewriteIfBare is a no-op.
        ((CommandManager)(Object)this).execute(newResults, rewritten);
        ci.cancel();
    }
}

