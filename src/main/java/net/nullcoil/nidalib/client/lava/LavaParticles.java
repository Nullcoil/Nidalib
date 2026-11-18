package net.nullcoil.nidalib.client.lava;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.nullcoil.nidalib.Nidalib;
import net.nullcoil.nidalib.lava.LavaHeat;
import net.nullcoil.nidalib.lava.LavaStage;
import net.nullcoil.nidalib.mixin.SingleQuadParticleAccessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Lava particles in their lava stage's colors.
 *
 * <p>Each particle commits to one stage when it spawns. Inside a crossfade, it picks the
 * upper stage with the crossfade's blend as the chance, so a border spits a mix of both
 * colors in the right proportion rather than an in-between color neither stage has.
 */
public final class LavaParticles {
    /** Template for the particle atlas's generated ember sprites (see atlases/particles.json). */
    public static final String LAVA = "nidalib:lava_{stage}";

    private static final Map<Long, Integer> COLORS = new HashMap<>();
    private static final Map<Identifier, Identifier> TEXTURES = new HashMap<>();

    private LavaParticles() {}

    /** The stage a particle spawned here should use. */
    public static LavaStage stageAt(Level level, double x, double y, double z, RandomSource random) {
        float heat = LavaHeat.faded(level, BlockPos.containing(x, y, z));
        int lower = Mth.clamp(Mth.floor(heat), 0, LavaStage.MAX);
        float blend = heat - lower;
        int stage = lower < LavaStage.MAX && random.nextFloat() < blend ? lower + 1 : lower;
        return LavaStage.values()[stage];
    }

    /** Whether there's lava at or just under a position (ripples and splashes sit on the surface). */
    public static boolean isLava(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return level.getFluidState(pos).is(FluidTags.LAVA) || level.getFluidState(pos.below()).is(FluidTags.LAVA);
    }

    /** An RGB color (alpha ignored) run through a stage, cached since the color math is slow. */
    public static int recolor(int rgb, LavaStage stage) {
        if (!stage.isGenerated()) return rgb;
        long key = ((long) stage.ordinal() << 32) | (rgb & 0xFFFFFFL);
        Integer cached = COLORS.get(key);
        if (cached != null) return cached;

        // The cooling drip fades through a new color every tick; keep the cache from growing forever
        if (COLORS.size() > 4096) COLORS.clear();
        int result = stage.apply(0xFF000000 | rgb) & 0xFFFFFF;
        COLORS.put(key, result);
        return result;
    }

    /** Runs a particle's current tint through a stage. */
    public static void recolor(SingleQuadParticle particle, LavaStage stage) {
        if (!stage.isGenerated()) return;
        SingleQuadParticleAccessor colors = (SingleQuadParticleAccessor) particle;
        float[] rgb = recolor(colors.nidalib$getRCol(), colors.nidalib$getGCol(), colors.nidalib$getBCol(), stage);
        particle.setColor(rgb[0], rgb[1], rgb[2]);
    }

    /** A float color run through a stage. */
    public static float[] recolor(float r, float g, float b, LavaStage stage) {
        int rgb = (to8bit(r) << 16) | (to8bit(g) << 8) | to8bit(b);
        int result = recolor(rgb, stage);
        return new float[] {((result >> 16) & 0xFF) / 255f, ((result >> 8) & 0xFF) / 255f, (result & 0xFF) / 255f};
    }

    private static int to8bit(float c) {
        return Mth.clamp(Math.round(c * 255), 0, 255);
    }

    /** A generated sprite from the particle atlas, or the fallback if it isn't there. */
    public static TextureAtlasSprite particleSprite(String template, LavaStage stage, TextureAtlasSprite fallback) {
        if (!stage.isGenerated()) return fallback;
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES)
                .getSprite(LavaStageSpriteSource.stageId(template, stage));
        return sprite.contents().name().getPath().equals("missingno") ? fallback : sprite;
    }

    /**
     * The generated stage copy of a mod's colored particle sprite, named
     * nidalib:&lt;namespace&gt;/&lt;path&gt;_&lt;stage&gt; (see atlases/particles.json), or the original if
     * there isn't one.
     */
    public static TextureAtlasSprite particleSprite(TextureAtlasSprite original, LavaStage stage) {
        Identifier name = original.contents().name();
        return particleSprite("nidalib:" + name.getNamespace() + "/" + name.getPath() + "_{stage}", stage, original);
    }

    /**
     * A standalone texture (not an atlas sprite, e.g. Subtle Effects' splash frames) recolored
     * to a stage. Made the first time it's asked for and kept for the session.
     */
    public static Identifier texture(Identifier texture, LavaStage stage) {
        if (!stage.isGenerated()) return texture;
        Identifier id = Nidalib.id("lava_stage/" + stage.id + "/" + texture.getNamespace() + "/" + texture.getPath());
        Identifier made = TEXTURES.get(id);
        if (made != null) return made;

        Minecraft minecraft = Minecraft.getInstance();
        try (InputStream in = minecraft.getResourceManager().open(texture)) {
            NativeImage image = NativeImage.read(in);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setPixel(x, y, stage.apply(image.getPixel(x, y)));
                }
            }
            minecraft.getTextureManager().register(id, new DynamicTexture(id::toString, image));
            made = id;
        } catch (IOException e) {
            Nidalib.LOGGER.warn("Couldn't recolor {} for lava stage {}", texture, stage.id, e);
            made = texture;
        }
        TEXTURES.put(id, made);
        return made;
    }

    /** The level particles live in (for mixins into classes that don't keep it). */
    public static ClientLevel level() {
        return Minecraft.getInstance().level;
    }
}
