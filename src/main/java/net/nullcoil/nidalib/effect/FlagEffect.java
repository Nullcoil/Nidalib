package net.nullcoil.nidalib.effect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class FlagEffect extends MobEffect {
    public FlagEffect(MobEffectCategory category, int color) { super(category, color); }

    public FlagEffect (MobEffectCategory category, int color, ParticleOptions particle) {
        super(category, color, particle);
    }
}
