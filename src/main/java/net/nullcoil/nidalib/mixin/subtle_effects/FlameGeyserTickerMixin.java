package net.nullcoil.nidalib.mixin.subtle_effects;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.nullcoil.nidalib.lava.LavaFire;
import net.nullcoil.nidalib.lava.LavaHeat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Subtle Effects' flame geysers shoot soul fire only in the Soul Sand Valley (a hardcoded
 * biome check). This makes them shoot soul fire wherever the lava heat is soul, by the same
 * rule as soul lava fire ({@link LavaFire#isSoul}), so any biome a mod gives soul heat counts.
 */
@Mixin(targets = "einstein.subtle_effects.ticking.tickers.geyser.FlameGeyserTicker")
public abstract class FlameGeyserTickerMixin {
    /** Grabs the level and position of the biome check, for the heat lookup below. */
    @WrapOperation(method = "geyserTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> nidalib$rememberPos(Level level, BlockPos pos, Operation<Holder<Biome>> original,
                                             @Share("level") LocalRef<Level> levelRef, @Share("pos") LocalRef<BlockPos> posRef) {
        levelRef.set(level);
        posRef.set(pos);
        return original.call(level, pos);
    }

    @ModifyExpressionValue(method = "geyserTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/resources/ResourceKey;)Z"))
    private boolean nidalib$soulHeat(boolean isSoulSandValley,
                                     @Share("level") LocalRef<Level> levelRef, @Share("pos") LocalRef<BlockPos> posRef) {
        return isSoulSandValley || LavaFire.isSoul(LavaHeat.faded(levelRef.get(), posRef.get()));
    }
}
