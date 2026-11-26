package net.nullcoil.nidalib.mixin.climbing;

import net.minecraft.world.entity.monster.spider.Spider;
import net.nullcoil.nidalib.climbing.WallClimbing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spiders override onClimbable (true whenever they bump a wall), which skips the LivingEntity
 * hook. While a registered spider is using Nidalib's climbing, vanilla's would push it up every
 * tick it touches the wall, so it's off. Spiders that aren't registered are untouched.
 */
@Mixin(Spider.class)
public abstract class WallClimberSpiderMixin {
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void nidalib$noVanillaClimb(CallbackInfoReturnable<Boolean> cir) {
        if (WallClimbing.get((Spider) (Object) this) != null) cir.setReturnValue(false);
    }
}
