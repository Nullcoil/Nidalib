package net.nullcoil.nidalib.mixin.subtle_effects;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.nullcoil.nidalib.client.lava.LavaParticles;
import net.nullcoil.nidalib.mixin.ParticlePositionAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Subtle Effects' floating sparks (only spawned over lava and down the sides of falling
 * lava) in the lava stage's colors.
 */
@Mixin(targets = "einstein.subtle_effects.particle.SparkParticle$FloatingProvider")
public abstract class SparkProviderMixin {
    // Name only, no parameters: the first createParticle is the real one (the bridge method
    // javac adds for generics comes after it), and it keeps working if the parameters change
    @ModifyReturnValue(method = "createParticle", at = @At("RETURN"))
    private Particle nidalib$lavaStage(Particle particle) {
        ClientLevel level = LavaParticles.level();
        if (level == null || particle == null) return particle;
        ParticlePositionAccessor pos = (ParticlePositionAccessor) particle;
        double x = pos.nidalib$getX(), y = pos.nidalib$getY(), z = pos.nidalib$getZ();
        if (particle instanceof SingleQuadParticle quad) {
            LavaParticles.recolor(quad, LavaParticles.stageAt(level, x, y, z, level.getRandom()));
        }
        return particle;
    }
}
