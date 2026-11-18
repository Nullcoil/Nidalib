package net.nullcoil.nidalib.mixin.subtle_effects;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.RandomSource;
import net.nullcoil.nidalib.client.lava.LavaParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Subtle Effects' floating sparks (only spawned over lava and down the sides of falling
 * lava) in the lava stage's colors.
 */
@Mixin(targets = "einstein.subtle_effects.particle.SparkParticle$FloatingProvider")
public abstract class SparkProviderMixin {
    @ModifyReturnValue(method = "createParticle(Leinstein/subtle_effects/particle/option/ColorProviderParticleOptions;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/util/RandomSource;)Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"))
    private Particle nidalib$lavaStage(Particle particle, @Local(argsOnly = true) ClientLevel level,
                                       @Local(argsOnly = true, ordinal = 0) double x, @Local(argsOnly = true, ordinal = 1) double y,
                                       @Local(argsOnly = true, ordinal = 2) double z, @Local(argsOnly = true) RandomSource random) {
        if (particle instanceof SingleQuadParticle quad) {
            LavaParticles.recolor(quad, LavaParticles.stageAt(level, x, y, z, random));
        }
        return particle;
    }
}
