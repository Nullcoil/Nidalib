package net.nullcoil.nidalib.mixin.voxy;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.nidalib.client.lava.LavaVoxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Gives Voxy a per-biome tint for lava: the biome's heat stage color (see LavaVoxy).
 * Voxy notices the tint asks for the biome and stores one color per biome.
 */
@Mixin(targets = "me.cortex.voxy.client.core.model.ModelFactory")
public abstract class VoxyTintSourcesMixin {
    @ModifyReturnValue(method = "getTintSources", at = @At("RETURN"))
    private static List<BlockTintSource> nidalib$lavaTint(List<BlockTintSource> original, @Local(argsOnly = true) BlockState block) {
        if (block.getBlock() instanceof LiquidBlock && block.getFluidState().is(FluidTags.LAVA)) {
            return List.of(LavaVoxy.TINT_SOURCE);
        }
        return original;
    }
}
