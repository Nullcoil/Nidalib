package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.nullcoil.nidalib.lava.LavaMapColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lava on map items takes its heat stage's color. Maps are drawn on the server (MapItem asks
 * each column's top block for its map color, lava included), so this runs server-side and
 * every player sees the same map.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class LavaMapColorMixin {
    @Shadow
    public abstract FluidState getFluidState();

    @ModifyReturnValue(method = "getMapColor", at = @At("RETURN"))
    private MapColor nidalib$heatColor(MapColor original, BlockGetter level, BlockPos pos) {
        // Only lava that still has vanilla's lava color (blocks that set their own stay theirs),
        // and only where biomes can be looked up
        if (original != MapColor.FIRE || !(level instanceof LevelReader reader)) return original;
        if (!this.getFluidState().is(FluidTags.LAVA)) return original;
        return LavaMapColor.at(reader, pos);
    }
}
