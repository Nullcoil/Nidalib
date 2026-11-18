package net.nullcoil.nidalib.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A particle's position, so mod compat hooks don't depend on how a provider's parameters look. */
@Mixin(Particle.class)
public interface ParticlePositionAccessor {
    @Accessor("x")
    double nidalib$getX();

    @Accessor("y")
    double nidalib$getY();

    @Accessor("z")
    double nidalib$getZ();
}
