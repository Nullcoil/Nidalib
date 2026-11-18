package net.nullcoil.nidalib.mixin.subtle_effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.nullcoil.nidalib.client.lava.LavaParticles;
import net.nullcoil.nidalib.lava.LavaStage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Subtle Effects' lava splashes in the lava stage's colors. Splash frames are colored
 * textures drawn on a model rather than atlas sprites, so each frame gets a recolored copy
 * made the first time it's needed.
 */
@Mixin(targets = "einstein.subtle_effects.particle.SplashParticle")
public abstract class SplashParticleMixin {
    @Shadow @Final private BlockPos.MutableBlockPos pos;
    @Shadow private Identifier texture;
    @Shadow private Identifier overlayTexture;

    @Unique private LavaStage nidalib$stage;

    @Inject(method = "setSpriteFromAge", at = @At("TAIL"))
    private void nidalib$lavaStage(CallbackInfo ci) {
        if (nidalib$stage == null) {
            ClientLevel level = LavaParticles.level();
            nidalib$stage = level != null && LavaParticles.isLava(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    ? LavaParticles.stageAt(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, level.getRandom())
                    : LavaStage.VANILLA;
        }
        if (!nidalib$stage.isGenerated()) return;

        if (texture != null) texture = LavaParticles.texture(texture, nidalib$stage);
        if (overlayTexture != null) overlayTexture = LavaParticles.texture(overlayTexture, nidalib$stage);
    }
}
