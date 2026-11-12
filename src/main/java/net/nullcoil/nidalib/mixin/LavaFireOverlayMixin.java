package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.nullcoil.nidalib.client.lava.LavaRenderPass;
import net.nullcoil.nidalib.lava.LavaFire;
import net.nullcoil.nidalib.lava.LavaStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tints the first-person "you're on fire" overlay with your lava fire's stage.
 *
 * <p>Priority 1100 makes this the outermost wrapper: Prometheus (if installed) wraps the
 * same lookup and doesn't pass through to inner wrappers, so this checks the sprite that
 * comes out of it and only replaces the default fire.
 */
@Mixin(value = ScreenEffectRenderer.class, priority = 1100)
public abstract class LavaFireOverlayMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/sprite/SpriteGetter;get(Lnet/minecraft/client/resources/model/sprite/SpriteId;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite nidalib$lavaFireOverlay(SpriteGetter instance, SpriteId sprite, Operation<TextureAtlasSprite> original) {
        TextureAtlasSprite result = original.call(instance, sprite);
        if (!result.contents().name().getPath().equals("block/fire_1")) return result;

        var player = Minecraft.getInstance().player;
        LavaStage stage = player == null ? null : LavaFire.get(player);
        return stage == null ? result : LavaRenderPass.stageSprite(LavaRenderPass.FIRE_1, stage, result);
    }
}
