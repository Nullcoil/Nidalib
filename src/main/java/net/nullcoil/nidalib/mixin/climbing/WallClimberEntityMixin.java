package net.nullcoil.nidalib.mixin.climbing;

import net.minecraft.world.entity.Entity;
import net.nullcoil.nidalib.climbing.WallClimber;
import net.nullcoil.nidalib.climbing.WallClimbing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class WallClimberEntityMixin {
    /** No gravity while it's on a wall or ceiling. */
    @Inject(method = "getGravity", at = @At("HEAD"), cancellable = true)
    private void nidalib$noGravityWhileClimbing(CallbackInfoReturnable<Double> cir) {
        if ((Object) this instanceof WallClimber climber) {
            WallClimbing climbing = climber.getWallClimbing();
            if (climbing != null && climbing.isClimbing()) cir.setReturnValue(0.0);
        }
    }
}
