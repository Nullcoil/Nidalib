package net.nullcoil.nidalib.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies nidalib.subtle_effects.mixins.json only when Subtle Effects is installed. */
public class SubtleEffectsMixinPlugin implements IMixinConfigPlugin {
    private static final boolean SUBTLE_EFFECTS = FabricLoader.getInstance().isModLoaded("subtle_effects");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return SUBTLE_EFFECTS;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
