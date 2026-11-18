package net.nullcoil.nidalib.mixin.subtle_effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
 * The ring Subtle Effects draws around a lava splash. Its frames are colored atlas sprites,
 * so each frame is swapped for its generated stage copy right after the particle picks it.
 */
@Mixin(targets = "einstein.subtle_effects.particle.SplashRippleParticle")
public abstract class SplashRippleParticleMixin extends SingleQuadParticle {
    @Shadow @Final private SpriteSet sprites;

    @Unique private LavaStage nidalib$stage;

    protected SplashRippleParticleMixin(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
    }

    @Inject(method = {"<init>", "tick"}, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Leinstein/subtle_effects/particle/SplashRippleParticle;setSpriteFromAge(Lnet/minecraft/client/particle/SpriteSet;)V"))
    private void nidalib$lavaStage(CallbackInfo ci) {
        if (nidalib$stage == null) {
            nidalib$stage = LavaParticles.isLava(this.level, this.x, this.y, this.z)
                    ? LavaParticles.stageAt(this.level, this.x, this.y, this.z, this.random)
                    : LavaStage.VANILLA;
        }
        if (!nidalib$stage.isGenerated()) return;
        this.setSprite(LavaParticles.particleSprite(sprites.get(this.age, this.lifetime), nidalib$stage));
    }
}
