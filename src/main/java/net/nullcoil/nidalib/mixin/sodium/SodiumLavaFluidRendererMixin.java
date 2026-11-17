package net.nullcoil.nidalib.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
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
 * {@code LavaFluidRendererMixin}, for Sodium. Sodium meshes fluids with its own
 * {@link DefaultFluidRenderer} instead of vanilla's {@code FluidRenderer}, so the vanilla
 * mixin never runs there. This does the same two things to Sodium's renderer: draw lava
 * once per heat stage (crossfading between two), and swap in the stage sprites.
 *
 * <p>The crossfade's opacity still comes from {@code LavaHeatClient.TINT_SOURCE}: Sodium
 * reads the lava model's tint source per block and keeps its alpha.
 *
 * <p>Only applied when Sodium is loaded (see {@code SodiumMixinPlugin}).
 */
@Mixin(DefaultFluidRenderer.class)
public abstract class SodiumLavaFluidRendererMixin {
    private static final float MIN_BLEND = 1f / 64f;

    @Shadow
    public abstract void render(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos,
                                BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder,
                                Material material, ColorProvider<FluidState> colorProvider, FluidModel sprites);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nidalib$heatPasses(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos,
                                    BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder,
                                    Material material, ColorProvider<FluidState> colorProvider, FluidModel sprites,
                                    CallbackInfo ci) {
        // Our own nested calls (below) render normally
        if (LavaRenderPass.active()) return;
        if (!fluidState.getType().isSame(Fluids.LAVA)) return;

        float heat = LavaHeatClient.at(level, blockPos);
        int lower = Math.min((int) Math.floor(heat), LavaStage.MAX);
        float blend = heat - lower;

        try {
            LavaRenderPass.begin(LavaStage.values()[lower], 255);
            this.render(level, blockState, fluidState, blockPos, offset, collector, meshBuilder, material, colorProvider, sprites);

            if (blend >= MIN_BLEND && lower < LavaStage.MAX) {
                LavaRenderPass.begin(LavaStage.values()[lower + 1], Math.round(blend * 255f));
                this.render(level, blockState, fluidState, blockPos, offset, collector, meshBuilder, material, colorProvider, sprites);
            }
        } finally {
            LavaRenderPass.end();
        }
        ci.cancel();
    }

    @ModifyExpressionValue(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/sprite/Material$Baked;sprite()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite nidalib$stageSprite(TextureAtlasSprite original) {
        return LavaRenderPass.swap(original);
    }
}
