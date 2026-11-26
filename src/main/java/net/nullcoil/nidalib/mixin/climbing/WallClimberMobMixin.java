package net.nullcoil.nidalib.mixin.climbing;

import net.minecraft.world.entity.Mob;
import net.nullcoil.nidalib.climbing.WallClimber;
import net.nullcoil.nidalib.climbing.WallClimbing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class WallClimberMobMixin {
    /** After the look control: aim the head in the surface's frame instead of the world's. */
    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void nidalib$alignClimbingHead(CallbackInfo ci) {
        if ((Object) this instanceof WallClimber climber) {
            WallClimbing climbing = climber.getWallClimbing();
            if (climbing != null) climbing.alignHead();
        }
    }
}
