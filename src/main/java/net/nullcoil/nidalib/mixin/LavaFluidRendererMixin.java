package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.nullcoil.nidalib.client.lava.LavaHeatClient;
import net.nullcoil.nidalib.client.lava.LavaRenderPass;
import net.nullcoil.nidalib.lava.LavaStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws lava with the texture of its biome's heat stage. Between two stages, the lava
 * is drawn twice: the cooler stage fully opaque, then the hotter stage on top at partial
 * opacity, so borders crossfade (vanilla -> yellow -> white -> infinite -> soul).
 *
 * <p>Plays nicely with BigWater: it swaps lava sprites at the same spot, and our swap
 * recognizes both vanilla's and BigWater's lava sprites, while BigWater leaves sprites
 * it doesn't know alone. The generated stages match the active texture's size, so
 * BigWater's scaling still lines up.
 */
@Mixin(FluidRenderer.class)
public abstract class LavaFluidRendererMixin {
    private static final String TESSELATE =
            "tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V";

    // Skip the second layer when it would be nearly invisible
    private static final float MIN_BLEND = 1f / 64f;

    @Shadow
    public abstract void tesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
                                   BlockState blockState, FluidState fluidState);

    @Inject(method = TESSELATE, at = @At("HEAD"), cancellable = true)
    private void nidalib$heatPasses(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
                                       BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        // Our own nested calls (below) render normally
        if (LavaRenderPass.active()) return;
        if (!fluidState.getType().isSame(Fluids.LAVA)) return;

        float heat = LavaHeatClient.at(level, pos);
        int lower = Math.min((int) Math.floor(heat), LavaStage.MAX);
        float blend = heat - lower;

        try {
            LavaRenderPass.begin(LavaStage.values()[lower], 255);
            this.tesselate(level, pos, output, blockState, fluidState);

            if (blend >= MIN_BLEND && lower < LavaStage.MAX) {
                LavaRenderPass.begin(LavaStage.values()[lower + 1], Math.round(blend * 255f));
                this.tesselate(level, pos, output, blockState, fluidState);
            }
        } finally {
            LavaRenderPass.end();
        }
        ci.cancel();
    }

    @ModifyExpressionValue(
            method = TESSELATE,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/sprite/Material$Baked;sprite()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite nidalib$stageSprite(TextureAtlasSprite original) {
        return LavaRenderPass.swap(original);
    }
}
