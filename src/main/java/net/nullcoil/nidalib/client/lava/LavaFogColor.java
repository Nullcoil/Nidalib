package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.nullcoil.nidalib.lava.LavaHeat;
import net.nullcoil.nidalib.lava.LavaStage;

/**
 * The under-lava fog color for a spot: vanilla's lava fog run through the same heat stages
 * as the lava texture, with the same border fade ({@link LavaHeat#faded}), so the fog always
 * matches the lava you're swimming in.
 */
@Environment(EnvType.CLIENT)
public final class LavaFogColor {
    private LavaFogColor() {}

    public static int at(LevelReader level, BlockPos pos, int vanillaColor) {
        float heat = LavaHeat.faded(level, pos);
        int lower = Math.min((int) Math.floor(heat), LavaStage.MAX);
        float blend = heat - lower;

        int from = stageColor(LavaStage.values()[lower], vanillaColor);
        if (blend <= 0f || lower >= LavaStage.MAX) return from;
        int to = stageColor(LavaStage.values()[lower + 1], vanillaColor);
        return lerp(blend, from, to);
    }

    private static int stageColor(LavaStage stage, int vanillaColor) {
        // Vanilla stays exactly vanilla (no round trip through the color math)
        return stage.isGenerated() ? stage.apply(vanillaColor) : vanillaColor;
    }

    private static int lerp(float t, int a, int b) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
