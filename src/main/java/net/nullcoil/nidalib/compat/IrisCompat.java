package net.nullcoil.nidalib.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether an Iris shader pack is active, without depending on Iris: looked up once through
 * Iris's public API ({@code IrisApi.getInstance().isShaderPackInUse()}), and false when
 * Iris isn't installed or the lookup fails.
 */
public final class IrisCompat {
    private IrisCompat() {}

    @Nullable
    private static final MethodHandle IS_SHADER_PACK_IN_USE = lookup();

    public static boolean shadersInUse() {
        if (IS_SHADER_PACK_IN_USE == null) return false;
        try {
            return (boolean) IS_SHADER_PACK_IN_USE.invoke();
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    private static MethodHandle lookup() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (Throwable t) {
            return null;
        }
    }
}
