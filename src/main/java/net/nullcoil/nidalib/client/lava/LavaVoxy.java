package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.nidalib.lava.LavaHeat;
import net.nullcoil.nidalib.lava.LavaStage;

import java.util.Map;

/**
 * Lava for LoD renderers like Voxy, which bake each block into one texture and color it
 * per biome with a tint multiplied on top, so they can't swap textures per position the
 * way the normal lava renderer does.
 *
 * <p>So for them lava is baked from a gray copy of its texture (brightness only), and
 * each biome's tint is its heat stage's average color, scaled to undo the gray copy's
 * darkening. Up close that loses the stage's exact pixel colors, but LoDs are far away,
 * where the average color is what shows. Biome borders don't crossfade; each biome gets
 * its own heat (fractional heats still mix their two stages' tints).
 */
@Environment(EnvType.CLIENT)
public final class LavaVoxy {
    private LavaVoxy() {}

    private static final ThreadLocal<Boolean> BAKING = new ThreadLocal<>();
    private static volatile Map<LavaStage, Integer> tints;

    /** Biome to lava tint, for {@code BlockAndTintGetter.getBlockTint}. */
    public static final ColorResolver RESOLVER = (biome, x, z) -> tint(heatOf(biome));

    /** Tint source an LoD renderer uses for lava instead of the normal one. */
    public static final BlockTintSource TINT_SOURCE = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return tint(LavaHeat.DEFAULT_HEAT);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return level.getBlockTint(pos, RESOLVER);
        }
    };

    /** Runs while an LoD renderer bakes a fluid; lava drawn meanwhile uses the gray copy. */
    public static void beginBake() {
        BAKING.set(Boolean.TRUE);
    }

    public static void endBake() {
        BAKING.remove();
    }

    public static boolean baking() {
        return BAKING.get() != null;
    }

    /** Set when the block atlas builds the gray lava copy (see LavaStageSpriteSource). */
    static void setTints(Map<LavaStage, Integer> stageTints) {
        tints = Map.copyOf(stageTints);
    }

    /** RGB tint for a heat, mixing the two nearest stages' tints. */
    public static int tint(float heat) {
        Map<LavaStage, Integer> current = tints;
        if (current == null) return 0xFFFFFF;
        int lower = Mth.clamp(Mth.floor(heat), 0, LavaStage.MAX);
        int upper = Math.min(lower + 1, LavaStage.MAX);
        float t = Mth.clamp(heat - lower, 0, 1);
        int a = current.get(LavaStage.values()[lower]), b = current.get(LavaStage.values()[upper]);
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int ca = (a >> shift) & 0xFF, cb = (b >> shift) & 0xFF;
            result |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return result;
    }

    private static float heatOf(Biome biome) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return LavaHeat.DEFAULT_HEAT;
        return level.registryAccess().lookupOrThrow(Registries.BIOME).getResourceKey(biome)
                .map(LavaHeat::get)
                .orElse(LavaHeat.DEFAULT_HEAT);
    }
}
