package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.nullcoil.nidalib.client.config.NidalibClientConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How lava crossfades between heat stages while shaders are on, where it has to stay fully
 * opaque (shaders treat translucent lava like water). Both modes step the blend in
 * 1/{@link #levels()}, using textures generated into the block atlas:
 * <ul>
 *   <li>{@link Mode#DITHER}: the hotter stage on top as a cut-out pixel pattern (4x4 or
 *       8x8 Bayer order) that keeps n/levels of the pixels.</li>
 *   <li>{@link Mode#PRE_BLEND}: one layer whose texture is already the two stages mixed
 *       n/levels of the way, pixel by pixel.</li>
 * </ul>
 * Only the chosen mode's textures are generated; each level is one more copy of every lava
 * texture, so more levels cost texture memory and resource reload time.
 */
@Environment(EnvType.CLIENT)
public final class LavaShaderBlend {
    private LavaShaderBlend() {}

    public enum Mode {
        DITHER("dither"),
        PRE_BLEND("pre_blend");

        public final String id;

        Mode(String id) {
            this.id = id;
        }

        public static Mode byId(String id) {
            for (Mode mode : values()) {
                if (mode.id.equals(id)) return mode;
            }
            return DITHER;
        }
    }

    /** The level counts the config slider steps through. */
    public static final List<Integer> LEVEL_OPTIONS = List.of(8, 16, 32, 64);

    // What the block atlas was last built with. The renderer must use the same: a config
    // change only takes effect after the resource reload rebuilds the atlas.
    private static volatile int builtLevels = LEVEL_OPTIONS.getFirst();
    private static volatile Mode builtMode = Mode.DITHER;

    public static int levels() {
        return builtLevels;
    }

    public static Mode mode() {
        return builtMode;
    }

    /** Called when the atlas starts building: locks in the configured settings. */
    static void beginAtlas() {
        NidalibClientConfig config = NidalibClientConfig.get();
        builtLevels = config.blendLevels();
        builtMode = config.shaderBlendMode();
    }

    /** Whether pixel (x, y) is kept by dither pattern {@code level} out of {@code levels}. */
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

    private static final Map<Integer, int[][]> BAYER = new ConcurrentHashMap<>();

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
