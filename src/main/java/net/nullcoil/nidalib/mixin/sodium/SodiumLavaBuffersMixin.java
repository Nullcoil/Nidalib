package net.nullcoil.nidalib.mixin.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl;
import net.nullcoil.nidalib.compat.sodium.SodiumLavaBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the section's mesh buffers to {@code SodiumLavaFluidRendererMixin} for the duration
 * of one fluid block: Sodium's {@code DefaultFluidRenderer} only gets the buffer for the
 * fluid's own render pass, and lava's base layer needs the solid one.
 */
@Mixin(FluidRendererImpl.class)
public abstract class SodiumLavaBuffersMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void nidalib$captureBuffers(CallbackInfo ci, @Local(argsOnly = true) ChunkBuildBuffers buffers) {
        SodiumLavaBuffers.set(buffers);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void nidalib$releaseBuffers(CallbackInfo ci) {
        SodiumLavaBuffers.clear();
    }
}
