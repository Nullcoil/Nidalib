package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.nullcoil.nidalib.client.config.NidalibClientConfig;

import java.util.List;

/**
 * How finely the lava crossfade is dithered under shaders, and the Bayer patterns for it.
 *
 * <p>With {@code L} levels, crossfade pattern {@code n} (1 to L - 1) keeps exactly n/L of
 * the pixels. Each level is one more generated copy of every lava stage texture, so more
 * levels cost texture memory and resource reload time.
 */
@Environment(EnvType.CLIENT)
public final class LavaDither {
    private LavaDither() {}

    /** The choices the config screen cycles through. */
    public static final List<Integer> OPTIONS = List.of(8, 16, 32, 64);

    // The level count the block atlas was last built with. The renderer must use the same
    // one: a config change only takes effect after the resource reload rebuilds the atlas.
    private static volatile int builtLevels = OPTIONS.getFirst();

    /** Levels the current atlas has patterns for. */
    public static int levels() {
        return builtLevels;
    }

    /** Called when the atlas starts building: locks in the configured level count. */
    static int beginAtlas() {
        int levels = NidalibClientConfig.get().blendLevels();
        builtLevels = levels;
        return levels;
    }

    /** Whether pixel (x, y) is kept by pattern {@code level} out of {@code levels}. */
    static boolean keeps(int x, int y, int level, int levels) {
        int side = side(levels);
        int[][] bayer = bayer(side);
        return bayer[y % side][x % side] * levels < level * side * side;
    }

    /** Smallest power-of-two tile with at least {@code levels} cells (8, 16 -> 4; 32, 64 -> 8). */
    private static int side(int levels) {
        int side = 1;
        while (side * side < levels) side *= 2;
        return side;
    }

    private static final java.util.Map<Integer, int[][]> BAYER = new java.util.concurrent.ConcurrentHashMap<>();

    /** Bayer matrix of a power-of-two size, values 0 to side² - 1. */
    private static int[][] bayer(int side) {
        return BAYER.computeIfAbsent(side, s -> {
            int[][] m = {{0}};
            for (int size = 1; size < s; size *= 2) {
                int[][] next = new int[size * 2][size * 2];
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        int v = m[y][x] * 4;
                        next[y][x] = v;
                        next[y][x + size] = v + 2;
                        next[y + size][x] = v + 3;
                        next[y + size][x + size] = v + 1;
                    }
                }
                m = next;
            }
            return m;
        });
    }
}
