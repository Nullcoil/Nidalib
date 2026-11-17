package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.nullcoil.nidalib.lava.LavaStage;
import org.jetbrains.annotations.Nullable;

/**
 * Lava heat stages for renderers that replace vanilla's fluid renderer (Sodium, and
 * modpacks built on it like Fabulously Optimized). Those never call vanilla's
 * {@code FluidRenderer.tesselate}, so {@code LavaFluidRendererMixin} never runs there,
 * but they do ask Fabric API's {@link FluidRenderHandler} for each block's sprites.
 *
 * <p>One sprite per block, so no two-layer crossfade: between two stages, each block
 * picks one of them with a chance equal to the blend, fixed per position. Borders come
 * out as a speckled transition instead of a smooth fade.
 */
@Environment(EnvType.CLIENT)
public final class LavaFluidRenderHandler implements FluidRenderHandler {
    private final FluidRenderHandler vanilla;

    private LavaFluidRenderHandler(FluidRenderHandler vanilla) {
        this.vanilla = vanilla;
    }

    /** Only call when a vanilla-renderer replacement (Sodium) is loaded. */
    public static void register() {
        FluidRenderHandler vanilla = FluidRenderHandlerRegistry.INSTANCE.get(Fluids.LAVA);
        FluidRenderHandlerRegistry.INSTANCE.register(Fluids.LAVA, Fluids.FLOWING_LAVA,
                new LavaFluidRenderHandler(vanilla));
    }

    @Override
    public TextureAtlasSprite[] getFluidSprites(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
        TextureAtlasSprite[] sprites = vanilla.getFluidSprites(view, pos, state);
        if (view == null || pos == null) return sprites; // items, tooltips etc.: plain lava

        LavaStage stage = stageAt(view, pos);
        if (!stage.isGenerated()) return sprites;

        TextureAtlasSprite[] swapped = sprites.clone();
        swapped[0] = LavaRenderPass.stageSprite(LavaRenderPass.LAVA_STILL, stage, sprites[0]);
        if (swapped.length > 1) {
            swapped[1] = LavaRenderPass.stageSprite(LavaRenderPass.LAVA_FLOW, stage, sprites[1]);
        }
        return swapped;
    }

    @Override
    public int getFluidColor(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
        return -1; // white: the stage textures carry their own colors
    }

    private static LavaStage stageAt(BlockAndTintGetter view, BlockPos pos) {
        float heat = LavaHeatClient.at(view, pos);
        int lower = Math.min((int) Math.floor(heat), LavaStage.MAX);
        float blend = heat - lower;

        if (lower < LavaStage.MAX && blend > 0f && dither(pos) < blend) {
            return LavaStage.values()[lower + 1];
        }
        return LavaStage.values()[Math.max(lower, 0)];
    }

    /** A fixed 0-1 value per block position, so the speckle pattern doesn't flicker. */
    private static float dither(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        return (h >>> 40) / (float) (1L << 24);
    }
}
