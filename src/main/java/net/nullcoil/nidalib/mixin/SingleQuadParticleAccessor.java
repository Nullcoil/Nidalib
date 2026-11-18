package net.nullcoil.nidalib.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a particle's tint, so lava particles can be recolored from whatever they were given. */
@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleAccessor {
    @Accessor("rCol")
    float nidalib$getRCol();

    @Accessor("gCol")
    float nidalib$getGCol();

    @Accessor("bCol")
    float nidalib$getBCol();
}
