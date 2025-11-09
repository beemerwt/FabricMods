package com.github.beemerwt.shulkerplace.mixin;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class ShulkerPlaceMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        LogUtils.getLogger().info("ShulkerPlace: checking mixin {}", mixinClassName);

        // Only apply Litematica bridge when Litematica is installed
        if (mixinClassName.endsWith("InventoryUtils_LitematicaBridge")) {
            LogUtils.getLogger().info("ShulkerPlace: enabling Litematica bridge mixin");
            return FabricLoader.getInstance().isModLoaded("litematica");
        }

        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }

    @Override public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
