package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ColorResolverRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.nidalib.Nidalib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-biome lava heat on the {@link LavaStage} scale (0 = red ... 5 = soul),
 * blended across biome borders like water color. Lava is drawn with its stage's
 * texture, crossfading between neighboring stages.
 *
 * <p>Every biome is {@link LavaStage#VANILLA} unless a mod says otherwise, so Nidalib
 * on its own changes nothing. From a client initializer:
 * <pre>{@code
 * LavaHeat.set(Biomes.CRIMSON_FOREST, LavaStage.RED);
 * LavaHeat.set(MY_BIOME, 2.5f); // halfway between yellow and white
 * }</pre>
 */
@Environment(EnvType.CLIENT)
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

    /**
     * Biome -> packed heat. The heat rides in the red channel so vanilla's biome blend
     * averages heat rather than colors; registered with Fabric so the blend cache knows it.
     */
    public static final ColorResolver HEAT_RESOLVER = (biome, x, z) -> pack(heatFor(biome));

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
        ColorResolverRegistry.register(HEAT_RESOLVER);
        SpriteSourceRegistry.register(Nidalib.id("lava_stages"), LavaStageSpriteSource.MAP_CODEC);
    }

    /** Blended heat at a position, 0 to {@link LavaStage#MAX}. */
    public static float at(BlockAndTintGetter level, BlockPos pos) {
        return unpack(level.getBlockTint(pos, HEAT_RESOLVER));
    }

    private static float heatFor(Biome biome) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return DEFAULT_HEAT;

        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        return biomes.getResourceKey(biome).map(LavaHeat::get).orElse(DEFAULT_HEAT);
    }

    private static int pack(float heat) {
        int value = Math.round(heat / LavaStage.MAX * 255f);
        return 0xFF000000 | (Mth.clamp(value, 0, 255) << 16);
    }

    private static float unpack(int packed) {
        return ((packed >> 16) & 0xFF) / 255f * LavaStage.MAX;
    }
}
