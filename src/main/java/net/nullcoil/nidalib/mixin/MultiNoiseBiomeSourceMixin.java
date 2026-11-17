package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.nullcoil.nidalib.worldgen.biome.ClimateParameterAccess;
import net.nullcoil.nidalib.worldgen.biome.SubBiomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin implements ClimateParameterAccess {
    @Shadow
    protected abstract Climate.ParameterList<Holder<Biome>> parameters();

    @Override
    public Climate.ParameterList<Holder<Biome>> nidalib$parameters() {
        return this.parameters();
    }

    /**
     * Every biome lookup (generation, /locate, F3) goes through this method, so swapping
     * its result covers all of them.
     *
     * <p>Wrapped as a whole rather than hooking {@code getNoiseBiome(TargetPoint)}: biome
     * mods like Biolith (used by Traverse and many packs) cancel this method at its head
     * and pick the biome themselves, so the inner method never runs. {@code @WrapMethod}
     * sits outside every other mixin's injections, so it sees the final pick either way.
     */
    @WrapMethod(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;")
    private Holder<Biome> nidalib$applySubBiomes(int x, int y, int z, Climate.Sampler sampler,
                                                 Operation<Holder<Biome>> original) {
        Holder<Biome> picked = original.call(x, y, z, sampler);
        // Skip the extra climate sample for the (vast majority of) biomes with no sub-biomes
        if (!SubBiomes.hasSubBiomes(picked)) return picked;

        Holder<Biome> sub = SubBiomes.pick(picked, sampler.sample(x, y, z), this.parameters());
        return sub != null ? sub : picked;
    }

    /** Lets structures, /locate and biome checks know sub-biomes can exist here. */
    @ModifyReturnValue(method = "collectPossibleBiomes", at = @At("RETURN"))
    private Stream<Holder<Biome>> nidalib$addSubBiomes(Stream<Holder<Biome>> original) {
        return SubBiomes.withSubBiomes(original);
    }
}
