package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import org.jetbrains.annotations.Nullable;

/**
 * Which stage and opacity the lava currently being drawn uses. Set by the
 * fluid renderer mixin around each pass; chunk meshing runs on several threads,
 * so it's per thread.
 */
@Environment(EnvType.CLIENT)
public final class LavaRenderPass {
    private LavaRenderPass() {}

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
        String kind;
        if (path.endsWith("lava_still")) kind = "still";
        else if (path.endsWith("lava_flow")) kind = "flow";
        else return original;

        TextureAtlasSprite sprite = lookup(LavaStageSpriteSource.spriteId(pass.stage(), kind));
        return sprite != null ? sprite : original;
    }

    @Nullable
    private static TextureAtlasSprite lookup(net.minecraft.resources.Identifier id) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(id);
        // Missing sprites come back as the missing texture rather than null
        return sprite.contents().name().getPath().equals("missingno") ? null : sprite;
    }
}
