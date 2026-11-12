package net.nullcoil.nidalib.worldgen.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/** Added to MultiNoiseBiomeSource by mixin: its climate parameter list. */
public interface ClimateParameterAccess {
    Climate.ParameterList<Holder<Biome>> nidalib$parameters();
}
