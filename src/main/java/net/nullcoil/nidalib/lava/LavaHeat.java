package net.nullcoil.nidalib.lava;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-biome lava heat on the {@link LavaStage} scale (0 = red ... 5 = soul).
 * Drives the lava fluid's color and the color of fire caught from lava.
 *
 * <p>Borders fade from the hotter side: a biome's lava is its pure color right up to its
 * border, and that heat then cools off into the cooler neighbor over {@link #FADE_DISTANCE}
 * blocks. Lava and fire both use {@link #faded}, so they always agree.
 *
 * <p>Every biome is {@link LavaStage#VANILLA} unless a mod says otherwise, so Nidalib
 * on its own changes nothing. Call from your <b>main</b> initializer (the server needs
 * it for lava fire, the client for rendering):
 * <pre>{@code
 * LavaHeat.set(Biomes.CRIMSON_FOREST, LavaStage.RED);
 * LavaHeat.set(MY_BIOME, 2.5f); // halfway between yellow and white
 * }</pre>
 */
public final class LavaHeat {
    private LavaHeat() {}

    /** Any biome without a heat: vanilla lava. */
    public static final float DEFAULT_HEAT = LavaStage.VANILLA.ordinal();

    private static final Map<ResourceKey<Biome>, Float> HEAT = new ConcurrentHashMap<>();

    /** Sets a biome's lava to a stage. Later calls for the same biome win. */
    public static void set(ResourceKey<Biome> biome, LavaStage stage) {
        set(biome, stage.ordinal());
    }

    /** Sets a biome's lava heat, 0 to {@link LavaStage#MAX}; fractions mix two stages. */
    public static void set(ResourceKey<Biome> biome, float heat) {
        HEAT.put(biome, Mth.clamp(heat, 0f, LavaStage.MAX));
    }

    /** A biome's configured heat, or {@link #DEFAULT_HEAT}. */
    public static float get(ResourceKey<Biome> biome) {
        return HEAT.getOrDefault(biome, DEFAULT_HEAT);
    }

    /** How many blocks a hotter biome's heat reaches into a cooler neighbor. */
    public static final int FADE_DISTANCE = 8;

    /** Unblended heat of the biome at a position. Works on both sides. */
    public static float at(Level level, BlockPos pos) {
        return heatOf(level.getBiome(pos));
    }

    /**
     * Heat at a position with border fades applied. Works on both sides.
     *
     * <p>Starts from this spot's own biome, then looks at the biome cells within
     * {@link #FADE_DISTANCE}: any hotter one pulls the heat up, fully at its edge and
     * fading linearly to nothing at the fade distance. So the transition always starts at
     * the hotter biome's color and lives entirely on the cooler side.
     */
    public static float faded(LevelReader level, BlockPos pos) {
        int x = pos.getX(), z = pos.getZ();
        int qx = QuartPos.fromBlock(x), qy = QuartPos.fromBlock(pos.getY()), qz = QuartPos.fromBlock(z);

        float own = heatOf(level.getNoiseBiome(qx, qy, qz));
        float heat = own;
        int reach = Mth.ceil(FADE_DISTANCE / 4f) + 1; // in biome cells (4 blocks wide)

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx == 0 && dz == 0) continue;

                float other = heatOf(level.getNoiseBiome(qx + dx, qy, qz + dz));
                if (other <= heat) continue;

                // Distance from this block to the nearest block of that cell
                int minX = QuartPos.toBlock(qx + dx), minZ = QuartPos.toBlock(qz + dz);
                int gapX = Math.max(0, Math.max(minX - x, x - (minX + 3)));
                int gapZ = Math.max(0, Math.max(minZ - z, z - (minZ + 3)));
                float distance = Mth.sqrt(gapX * gapX + gapZ * gapZ);
                if (distance >= FADE_DISTANCE) continue;

                heat = Math.max(heat, other - (other - own) * (distance / FADE_DISTANCE));
            }
        }
        return heat;
    }

    private static float heatOf(Holder<Biome> biome) {
        return biome.unwrapKey().map(LavaHeat::get).orElse(DEFAULT_HEAT);
    }
}
