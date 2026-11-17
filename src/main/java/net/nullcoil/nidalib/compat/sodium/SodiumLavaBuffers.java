package net.nullcoil.nidalib.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import org.jetbrains.annotations.Nullable;

/**
 * The chunk section's mesh buffers while Sodium renders one fluid block, so the lava mixin
 * can send its opaque base layer to the solid pass. Per thread: Sodium meshes chunks on
 * several threads at once. Only loaded when Sodium is (it's only used from Sodium mixins).
 */
public final class SodiumLavaBuffers {
    private SodiumLavaBuffers() {}

    private static final ThreadLocal<ChunkBuildBuffers> CURRENT = new ThreadLocal<>();

    public static void set(ChunkBuildBuffers buffers) {
        CURRENT.set(buffers);
    }

    public static void clear() {
        CURRENT.remove();
    }

    @Nullable
    public static ChunkBuildBuffers get() {
        return CURRENT.get();
    }
}
