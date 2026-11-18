package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.nidalib.compat.IrisCompat;
import net.nullcoil.nidalib.lava.LavaStage;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The lava cauldron's model, with the lava inside drawn in the heat stage of the cauldron's
 * biome, crossfading at borders exactly like lava itself: an opaque base layer, then a
 * translucent layer of the hotter stage on top, or with shaders on, a dithered cut-out
 * layer or a single pre-blended one ({@link LavaShaderBlend}).
 *
 * <p>The cauldron itself is unchanged: only quads textured with the lava sprite are touched,
 * and only their texture coordinates (moved onto the stage sprite), opacity and layer.
 * Goes through Fabric's renderer API, so it works with Sodium too.
 */
@Environment(EnvType.CLIENT)
public class LavaCauldronModel extends WrapperBlockStateModel {
    private static final Identifier LAVA_STILL = Identifier.withDefaultNamespace("block/lava_still");
    private static final float MIN_BLEND = 1f / 64f;

    public LavaCauldronModel(BlockStateModel wrapped) {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<@Nullable Direction> cullTest) {
        TextureAtlasSprite lava = LavaRenderPass.atlasSprite(LAVA_STILL);
        if (lava == null) {
            super.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }

        float heat = LavaHeatClient.at(level, pos);
        int lower = Math.min((int) Math.floor(heat), LavaStage.MAX);
        float blend = heat - lower;
        LavaStage lowerStage = LavaStage.values()[lower];
        boolean crossfade = blend >= MIN_BLEND && lower < LavaStage.MAX;

        if (crossfade && IrisCompat.shadersInUse()) {
            // Shaders: stay fully opaque (same rules as lava in the world)
            LavaStage upperStage = LavaStage.values()[lower + 1];
            int levels = LavaShaderBlend.levels();
            int step = Math.round(blend * levels);

            if (step <= 0 || step >= levels) {
                LavaRenderPass.begin(step >= levels ? upperStage : lowerStage, 255);
                emitPass(emitter, level, pos, state, random, cullTest, lava, false, null);
            } else if (LavaShaderBlend.mode() == LavaShaderBlend.Mode.PRE_BLEND) {
                LavaRenderPass.beginBlended(lowerStage, step);
                emitPass(emitter, level, pos, state, random, cullTest, lava, false, null);
            } else {
                LavaRenderPass.begin(lowerStage, 255);
                emitPass(emitter, level, pos, state, random, cullTest, lava, false, null);
                LavaRenderPass.beginDithered(upperStage, step);
                emitPass(emitter, level, pos, state, random, cullTest, lava, true, ChunkSectionLayer.CUTOUT);
            }
        } else {
            // The cauldron plus the cooler stage, then the hotter stage fading in on top
            LavaRenderPass.begin(lowerStage, 255);
            emitPass(emitter, level, pos, state, random, cullTest, lava, false, null);

            if (crossfade) {
                LavaRenderPass.begin(LavaStage.values()[lower + 1], Math.round(blend * 255f));
                emitPass(emitter, level, pos, state, random, cullTest, lava, true, ChunkSectionLayer.TRANSLUCENT);
            }
        }
    }

    /**
     * Emits the model with the current {@link LavaRenderPass} applied to its lava quads.
     *
     * @param lavaOnly drop every other quad (the second, crossfade pass)
     * @param layer    layer to move the lava quads to, or null to keep theirs
     */
    private void emitPass(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<@Nullable Direction> cullTest,
                          TextureAtlasSprite lava, boolean lavaOnly, @Nullable ChunkSectionLayer layer) {
        TextureAtlasSprite target = LavaRenderPass.swap(lava);
        int alpha = LavaRenderPass.alpha();

        emitter.pushTransform(quad -> {
            if (!isOnSprite(quad, lava)) return !lavaOnly;

            if (target != lava) moveToSprite(quad, lava, target);
            if (layer != null) quad.chunkLayer(layer);
            if (alpha < 255) {
                for (int i = 0; i < 4; i++) {
                    quad.color(i, (alpha << 24) | (quad.color(i) & 0x00FFFFFF));
                }
            }
            return true;
        });
        try {
            super.emitQuads(emitter, level, pos, state, random, cullTest);
        } finally {
            emitter.popTransform();
            LavaRenderPass.end();
        }
    }

    /** Whether all four corners of the quad sample inside the sprite. */
    private static boolean isOnSprite(MutableQuadView quad, TextureAtlasSprite sprite) {
        if (quad.atlas() != QuadAtlas.BLOCK) return false;
        for (int i = 0; i < 4; i++) {
            float u = quad.u(i), v = quad.v(i);
            if (u < sprite.getU0() || u > sprite.getU1() || v < sprite.getV0() || v > sprite.getV1()) return false;
        }
        return true;
    }

    /** Keeps each corner at the same spot within the texture, moved from one sprite to the other. */
    private static void moveToSprite(MutableQuadView quad, TextureAtlasSprite from, TextureAtlasSprite to) {
        for (int i = 0; i < 4; i++) {
            float su = (quad.u(i) - from.getU0()) / (from.getU1() - from.getU0());
            float sv = (quad.v(i) - from.getV0()) / (from.getV1() - from.getV0());
            quad.uv(i, to.getU0() + su * (to.getU1() - to.getU0()), to.getV0() + sv * (to.getV1() - to.getV0()));
        }
    }

    // The lava depends on the biome at this position, so there's no position-free geometry to cache
    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return null;
    }
}
