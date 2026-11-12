package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.nullcoil.nidalib.lava.LavaStage;
import org.jetbrains.annotations.Nullable;

/**
 * Which stage and opacity the lava currently being drawn uses. Set by the
 * fluid renderer mixin around each pass; chunk meshing runs on several threads,
 * so it's per thread. Also home to the stage sprite lookups.
 */
@Environment(EnvType.CLIENT)
public final class LavaRenderPass {
    private LavaRenderPass() {}

    public static final String LAVA_STILL = "nidalib:block/lava_{stage}_still";
    public static final String LAVA_FLOW = "nidalib:block/lava_{stage}_flow";
    public static final String FIRE_0 = "nidalib:block/fire_0_{stage}";
    public static final String FIRE_1 = "nidalib:block/fire_1_{stage}";

    private record Pass(LavaStage stage, int alpha) {}

    private static final ThreadLocal<Pass> CURRENT = new ThreadLocal<>();

    public static boolean active() {
        return CURRENT.get() != null;
    }

    public static void begin(LavaStage stage, int alpha) {
        CURRENT.set(new Pass(stage, alpha));
    }

    public static void end() {
        CURRENT.remove();
    }

    /** Opacity for the tint: 255 outside a pass. */
    public static int alpha() {
        Pass pass = CURRENT.get();
        return pass == null ? 255 : pass.alpha();
    }

    /**
     * The sprite to draw instead of the given lava sprite, or the original when no
     * swap applies (not in a pass, vanilla stage, or not a lava sprite at all).
     * Works whether the original is vanilla's or BigWater's lava texture.
     */
    public static TextureAtlasSprite swap(TextureAtlasSprite original) {
        Pass pass = CURRENT.get();
        if (pass == null || !pass.stage().isGenerated()) return original;

        String path = original.contents().name().getPath();
        String template;
        if (path.endsWith("lava_still")) template = LAVA_STILL;
        else if (path.endsWith("lava_flow")) template = LAVA_FLOW;
        else return original;

        return stageSprite(template, pass.stage(), original);
    }

    /** A generated stage sprite, or the fallback if it isn't in the atlas. */
    public static TextureAtlasSprite stageSprite(String template, LavaStage stage, TextureAtlasSprite fallback) {
        TextureAtlasSprite sprite = atlasSprite(LavaStageSpriteSource.stageId(template, stage));
        return sprite != null ? sprite : fallback;
    }

    @Nullable
    private static TextureAtlasSprite atlasSprite(Identifier id) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(id);
        // Missing sprites come back as the missing texture rather than null
        return sprite.contents().name().getPath().equals("missingno") ? null : sprite;
    }
}
