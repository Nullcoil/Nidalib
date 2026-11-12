package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.nullcoil.nidalib.client.lava.LavaFireRenderState;
import net.nullcoil.nidalib.client.lava.LavaRenderPass;
import net.nullcoil.nidalib.lava.LavaStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws flames on burning entities in their lava stage's colors.
 *
 * <p>Priority 900 makes this the innermost wrapper around {@code prepare}, so if
 * Prometheus is installed (it wraps the same call to pick sprites per fire type) it runs
 * first and this gets the final say. Only default flames are recolored: if Prometheus
 * has already picked another fire type's sprites, they're left alone.
 */
@Mixin(value = FlameFeatureRenderer.class, priority = 900)
public abstract class LavaFireFlameMixin {
    @WrapOperation(method = "buildGroup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer;prepare(Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer$Submit;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void nidalib$lavaFlames(FlameFeatureRenderer instance, FlameFeatureRenderer.Submit submit, VertexConsumer buffer,
                                    TextureAtlasSprite sprite0, TextureAtlasSprite sprite1, Operation<Void> original) {
        LavaStage stage = ((LavaFireRenderState) submit.entityRenderState()).nidalib$getLavaFireStage();
        if (stage != null && sprite0.contents().name().getPath().equals("block/fire_0")) {
            sprite0 = LavaRenderPass.stageSprite(LavaRenderPass.FIRE_0, stage, sprite0);
            sprite1 = LavaRenderPass.stageSprite(LavaRenderPass.FIRE_1, stage, sprite1);
        }
        original.call(instance, submit, buffer, sprite0, sprite1);
    }
}
