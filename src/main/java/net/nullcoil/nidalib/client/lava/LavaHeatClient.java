package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.nidalib.Nidalib;
import net.nullcoil.nidalib.lava.LavaHeat;
import net.nullcoil.nidalib.lava.LavaStage;

/**
 * Client side of {@link LavaHeat}: the heat the lava renderer draws with, the tint that
 * carries the crossfade's opacity, and registering the stage texture generator.
 */
@Environment(EnvType.CLIENT)
public final class LavaHeatClient {
    private LavaHeatClient() {}

    /**
     * Lava's tint: white, so the stage textures show their own colors, with the alpha of
     * the current render pass (fully opaque, or partial for the crossfade layer).
     */
    public static final BlockTintSource TINT_SOURCE = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return (LavaRenderPass.alpha() << 24) | 0xFFFFFF;
        }
    };

    /** Called by Nidalib's client initializer. */
    public static void init() {
        SpriteSourceRegistry.register(Nidalib.id("lava_stages"), LavaStageSpriteSource.MAP_CODEC);
    }

    /**
     * Heat to draw lava with at a position, 0 to {@link LavaStage#MAX}: the same
     * hot-side fade the server uses for lava fire, so lava and its fire always match.
     */
    public static float at(BlockAndTintGetter level, BlockPos pos) {
        ClientLevel clientLevel = Minecraft.getInstance().level;
        return clientLevel == null ? LavaHeat.DEFAULT_HEAT : LavaHeat.faded(clientLevel, pos);
    }
}
