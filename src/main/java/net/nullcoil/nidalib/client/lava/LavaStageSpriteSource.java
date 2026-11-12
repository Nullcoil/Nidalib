package net.nullcoil.nidalib.client.lava;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Atlas sprite source that generates every non-vanilla {@link LavaStage} from the
 * active lava texture while the atlas is built. Uses the first texture in "sources"
 * that exists, so it follows resource packs and BigWater's packs, and each copy keeps
 * the original's size and animation.
 *
 * <pre>{@code
 * { "type": "nidalib:lava_stages",
 *   "sources": ["bigwater:block/lava_still", "minecraft:block/lava_still"],
 *   "kind": "still" }
 * }</pre>
 * produces nidalib:block/lava_red_still, lava_yellow_still, and so on.
 */
@Environment(EnvType.CLIENT)
public record LavaStageSpriteSource(List<Identifier> sources, String kind) implements SpriteSource {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<LavaStageSpriteSource> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("sources").forGetter(LavaStageSpriteSource::sources),
            com.mojang.serialization.Codec.STRING.fieldOf("kind").forGetter(LavaStageSpriteSource::kind)
    ).apply(instance, LavaStageSpriteSource::new));

    /** The generated sprite for a stage, e.g. nidalib:block/lava_soul_flow. */
    public static Identifier spriteId(LavaStage stage, String kind) {
        return Identifier.fromNamespaceAndPath("nidalib", "block/lava_" + stage.id + "_" + kind);
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        for (Identifier source : sources) {
            Optional<Resource> resource = resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(source));
            if (resource.isEmpty()) continue;

            Resource original = resource.get();
            for (LavaStage stage : LavaStage.values()) {
                if (!stage.isGenerated()) continue;
                // Same pack and same .mcmeta (animation), recolored pixels
                output.add(spriteId(stage, kind),
                        new Resource(original.source(), () -> recolor(original, stage), original::metadata));
            }
            return;
        }
        LOGGER.warn("No lava texture found for {} stages (tried {})", kind, sources);
    }

    @Override
    public MapCodec<LavaStageSpriteSource> codec() {
        return MAP_CODEC;
    }

    private static InputStream recolor(Resource original, LavaStage stage) throws IOException {
        try (InputStream in = original.open(); NativeImage image = NativeImage.read(in)) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setPixel(x, y, stage.apply(image.getPixel(x, y)));
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
