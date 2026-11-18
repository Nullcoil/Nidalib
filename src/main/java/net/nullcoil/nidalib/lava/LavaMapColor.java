package net.nullcoil.nidalib.lava;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.MapColor;

/**
 * Lava's color on map items for each heat stage. Maps can only use Minecraft's fixed
 * palette of map colors, so each stage gets the closest one to its lava (vanilla lava is
 * {@link MapColor#FIRE}, as in vanilla). Between two stages, map pixels pick one or the
 * other in a fixed 4x4 pattern weighted by the blend, so borders show as a dithered edge
 * the way maps draw everything else.
 */
public final class LavaMapColor {
    private LavaMapColor() {}

    // Closest palette colors to each stage's average lava color (CIE Lab distance)
    private static final MapColor[] COLORS = new MapColor[LavaStage.values().length];
    static {
        COLORS[LavaStage.RED.ordinal()]      = MapColor.CRIMSON_NYLIUM;
        COLORS[LavaStage.VANILLA.ordinal()]  = MapColor.FIRE;
        COLORS[LavaStage.YELLOW.ordinal()]   = MapColor.COLOR_ORANGE;
        COLORS[LavaStage.WHITE.ordinal()]    = MapColor.TERRACOTTA_WHITE;
        COLORS[LavaStage.INFINITE.ordinal()] = MapColor.COLOR_LIGHT_BLUE;
        COLORS[LavaStage.SOUL.ordinal()]     = MapColor.DIAMOND;
    }

    // 4x4 Bayer order, so blends spread evenly
    private static final int[][] BAYER = {
            { 0,  8,  2, 10},
            {12,  4, 14,  6},
            { 3, 11,  1,  9},
            {15,  7, 13,  5}
    };

    public static MapColor of(LavaStage stage) {
        return COLORS[stage.ordinal()];
    }

    /** The map color for lava at this spot, from the biome heat and border fade. */
    public static MapColor at(LevelReader level, BlockPos pos) {
        float heat = LavaHeat.faded(level, pos);
        int lower = Math.min((int) Math.floor(heat), LavaStage.MAX);
        float blend = heat - lower;

        int stage = lower;
        if (lower < LavaStage.MAX && (BAYER[pos.getZ() & 3][pos.getX() & 3] + 0.5f) / 16f < blend) {
            stage = lower + 1;
        }
        return COLORS[stage];
    }
}
