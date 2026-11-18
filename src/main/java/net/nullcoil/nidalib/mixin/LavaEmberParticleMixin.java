package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.LavaParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.nullcoil.nidalib.client.lava.LavaParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The embers lava pops out use the generated sprite of the lava stage where they spawn
 * (the texture is colored, not tinted, so it has to be a different sprite).
 */
@Mixin(LavaParticle.class)
public abstract class LavaEmberParticleMixin extends SingleQuadParticle {
    protected LavaEmberParticleMixin(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nidalib$stageSprite(CallbackInfo ci, @Local(argsOnly = true) TextureAtlasSprite sprite) {
        this.setSprite(LavaParticles.particleSprite(LavaParticles.LAVA,
                LavaParticles.stageAt(this.level, this.x, this.y, this.z, this.random), sprite));
    }
}
