package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.nullcoil.nidalib.worldgen.biome.SubBiomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Shadow
    protected abstract Climate.ParameterList<Holder<Biome>> parameters();

    /**
     * Every biome lookup (generation, /locate, F3) ends here,
     * so swapping the result covers all of them.
     */
    @ModifyReturnValue(
            method = "getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;",
            at = @At("RETURN"))
    private Holder<Biome> nidalib$applySubBiomes(Holder<Biome> picked,
                                                 @Local(argsOnly = true) Climate.TargetPoint point) {
        Holder<Biome> sub = SubBiomes.pick(picked, point, this.parameters());
        return sub != null ? sub : picked;
    }

    /** Lets structures, /locate and biome checks know sub-biomes can exist here. */
    @ModifyReturnValue(method = "collectPossibleBiomes", at = @At("RETURN"))
    private Stream<Holder<Biome>> nidalib$addSubBiomes(Stream<Holder<Biome>> original) {
        return SubBiomes.withSubBiomes(original);
    }
}
