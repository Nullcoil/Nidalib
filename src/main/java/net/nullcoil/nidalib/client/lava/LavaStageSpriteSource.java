package net.nullcoil.nidalib.client.lava;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.nullcoil.nidalib.lava.LavaStage;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Atlas sprite source that generates every non-vanilla {@link LavaStage} of a texture
 * while the atlas is built. Uses the first texture in "sources" that exists, so it follows
 * resource packs (and BigWater's packs), and each copy keeps the original's size and
 * animation. "{stage}" in "output" is replaced with the stage name.
 *
 * <pre>{@code
 * { "type": "nidalib:lava_stages",
 *   "sources": ["bigwater:block/lava_still", "minecraft:block/lava_still"],
 *   "output": "nidalib:block/lava_{stage}_still",
 *   "shader_blend": true }
 * }</pre>
 * produces nidalib:block/lava_red_still, lava_yellow_still, and so on.
 * "scaled_lightness" uses {@link LavaStage#apply(int, boolean) scaled lightness}, which keeps
 * detail in bright textures (the lava bucket) where the usual added lightness would clip
 * them to white. With
 * "shader_blend", it also generates the textures {@link LavaShaderBlend} needs for the
 * configured mode: dither patterns, or pre-blended stage pairs. "optional" skips the
 * missing-texture warning, for textures from mods that may not be installed.
 */
@Environment(EnvType.CLIENT)
public record LavaStageSpriteSource(List<Identifier> sources, String output, boolean shaderBlend, boolean scaledLightness, boolean optional) implements SpriteSource {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<LavaStageSpriteSource> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("sources").forGetter(LavaStageSpriteSource::sources),
            Codec.STRING.fieldOf("output").forGetter(LavaStageSpriteSource::output),
            Codec.BOOL.optionalFieldOf("shader_blend", false).forGetter(LavaStageSpriteSource::shaderBlend),
            Codec.BOOL.optionalFieldOf("scaled_lightness", false).forGetter(LavaStageSpriteSource::scaledLightness),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(LavaStageSpriteSource::optional)
    ).apply(instance, LavaStageSpriteSource::new));

    /** Fills a "{stage}" template, e.g. "nidalib:block/fire_0_{stage}" -> nidalib:block/fire_0_red. */
    public static Identifier stageId(String template, LavaStage stage) {
        return Identifier.parse(template.replace("{stage}", stage.id));
    }

    /** A stage's dither pattern, e.g. nidalib:block/lava_soul_still_dither_3. */
    public static Identifier ditherId(String template, LavaStage stage, int level) {
        return Identifier.parse(template.replace("{stage}", stage.id) + "_dither_" + level);
    }

    /**
     * A stage pre-blended {@code level} steps toward the next hotter one,
     * e.g. nidalib:block/lava_infinite_to_soul_still_blend_3.
     */
    public static Identifier blendId(String template, LavaStage lower, int level) {
        LavaStage upper = LavaStage.values()[lower.ordinal() + 1];
        return Identifier.parse(template.replace("{stage}", lower.id + "_to_" + upper.id) + "_blend_" + level);
    }

    @Override
    public void run(ResourceManager resourceManager, Output out) {
        for (Identifier source : sources) {
            Optional<Resource> resource = resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(source));
            if (resource.isEmpty()) continue;

            Resource original = resource.get();
            // Each stage's recolored pixels are computed once and shared by every texture made
            // from them (the color math is the slow part, and there can be hundreds of copies)
            Supplier<Pixels> base = Suppliers.memoize(() -> Pixels.read(original));
            Map<LavaStage, Supplier<Pixels>> stages = new EnumMap<>(LavaStage.class);
            for (LavaStage stage : LavaStage.values()) {
                stages.put(stage, stage.isGenerated()
                        ? Suppliers.memoize(() -> base.get().recolored(stage, scaledLightness))
                        : base);
            }

            for (LavaStage stage : LavaStage.values()) {
                if (!stage.isGenerated()) continue;
                // Same pack and same .mcmeta (animation), recolored pixels
                out.add(stageId(output, stage),
                        new Resource(original.source(), () -> stages.get(stage).get().encode(), original::metadata));
            }

            if (shaderBlend) addShaderBlend(original, stages, out);
            return;
        }
        if (!optional) LOGGER.warn("No texture found for lava stages {} (tried {})", output, sources);
    }

    private void addShaderBlend(Resource original, Map<LavaStage, Supplier<Pixels>> stages, Output out) {
        LavaShaderBlend.beginAtlas();
        int levels = LavaShaderBlend.levels();

        for (LavaStage stage : LavaStage.values()) {
            for (int level = 1; level < levels; level++) {
                int lvl = level;
                if (LavaShaderBlend.mode() == LavaShaderBlend.Mode.DITHER) {
                    // Every stage that can be the upper half of a crossfade (all but the coolest)
                    if (stage.ordinal() == 0) continue;
                    out.add(ditherId(output, stage, level), new Resource(original.source(),
                            () -> stages.get(stage).get().dithered(lvl, levels).encode(), original::metadata));
                } else {
                    // Every stage that has a hotter neighbor to blend toward
                    if (stage.ordinal() == LavaStage.MAX) continue;
                    LavaStage upper = LavaStage.values()[stage.ordinal() + 1];
                    out.add(blendId(output, stage, level), new Resource(original.source(),
                            () -> stages.get(stage).get().blended(stages.get(upper).get(), lvl / (float) levels).encode(),
                            original::metadata));
                }
            }
        }
    }

    @Override
    public MapCodec<LavaStageSpriteSource> codec() {
        return MAP_CODEC;
    }

    /** A texture's pixels (ARGB), all animation frames included. */
    private record Pixels(int width, int height, int[] argb) {
        static Pixels read(Resource resource) {
            try (InputStream in = resource.open(); NativeImage image = NativeImage.read(in)) {
                int[] argb = new int[image.getWidth() * image.getHeight()];
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        argb[y * image.getWidth() + x] = image.getPixel(x, y);
                    }
                }
                return new Pixels(image.getWidth(), image.getHeight(), argb);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        Pixels recolored(LavaStage stage, boolean scaledLightness) {
            int[] result = new int[argb.length];
            for (int i = 0; i < argb.length; i++) result[i] = stage.apply(argb[i], scaledLightness);
            return new Pixels(width, height, result);
        }

        /** Only the dither pattern's pixels kept, the rest fully transparent. */
        Pixels dithered(int level, int levels) {
            int[] result = argb.clone();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // Animation frames are stacked vertically and multiples of the pattern
                    // size tall (4 or 8), so the pattern lines up in every frame
                    if (!LavaShaderBlend.keeps(x, y, level, levels)) result[y * width + x] &= 0x00FFFFFF;
                }
            }
            return new Pixels(width, height, result);
        }

        /** Every pixel mixed {@code t} of the way toward {@code other}'s (same size). */
        Pixels blended(Pixels other, float t) {
            int[] result = new int[argb.length];
            for (int i = 0; i < argb.length; i++) result[i] = lerp(argb[i], other.argb[i], t);
            return new Pixels(width, height, result);
        }

        private static int lerp(int a, int b, float t) {
            int result = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
                result |= Math.round(ca + (cb - ca) * t) << shift;
            }
            return result;
        }

        InputStream encode() throws IOException {
            try (NativeImage image = new NativeImage(width, height, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        image.setPixel(x, y, argb[y * width + x]);
                    }
                }
                // NativeImage can only encode PNGs to a file, so round-trip through a temp file
                Path temp = Files.createTempFile("nidalib_lava_", ".png");
                try {
                    image.writeToFile(temp);
                    return new ByteArrayInputStream(Files.readAllBytes(temp));
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
        }
    }
}
