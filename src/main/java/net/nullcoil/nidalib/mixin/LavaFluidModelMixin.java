package net.nullcoil.nidalib.mixin;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.nullcoil.nidalib.client.lava.LavaHeatClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidStateModelSet.class)
public class LavaFluidModelMixin {

    @Shadow
    @Final
    @Mutable
    private static FluidModel.Unbaked LAVA_MODEL;

    /**
     * Lava keeps vanilla's textures here (the renderer swaps in heat stages per block),
     * stays translucent so the crossfade layer can blend, and gets the tint source that
     * carries the crossfade's opacity.
     */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void nidalib$lavaModel(CallbackInfo ci) {
        LAVA_MODEL = new FluidModel.Unbaked(
                LAVA_MODEL.stillMaterial().withForceTranslucent(true),
                LAVA_MODEL.flowingMaterial().withForceTranslucent(true),
                LAVA_MODEL.overlayMaterial(),
                LavaHeatClient.TINT_SOURCE
        );
    }
}
