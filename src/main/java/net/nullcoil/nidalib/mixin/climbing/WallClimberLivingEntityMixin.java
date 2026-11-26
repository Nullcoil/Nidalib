package net.nullcoil.nidalib.mixin.climbing;

import net.minecraft.world.entity.LivingEntity;
import net.nullcoil.nidalib.climbing.WallClimbing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class WallClimberLivingEntityMixin {
    /** Ladder/spider climbing would fight the wall movement (it sets vertical speed on collisions). */
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void nidalib$noLadderWhileClimbing(CallbackInfoReturnable<Boolean> cir) {
        WallClimbing climbing = WallClimbing.get((LivingEntity) (Object) this);
        if (climbing != null && climbing.isClimbing()) cir.setReturnValue(false);
    }

    /**
     * Anything that can climb never takes fall damage: not off a wall, not after letting go, not
     * off a ledge. (Matched by name only, so it doesn't depend on 26.2's parameter list.)
     */
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void nidalib$noFallDamageForClimbers(CallbackInfoReturnable<Boolean> cir) {
        if (WallClimbing.get((LivingEntity) (Object) this) != null) cir.setReturnValue(false);
    }

    /** Client: ease the model toward its surface. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void nidalib$tickClimbingClient(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) {
            WallClimbing climbing = WallClimbing.get(self);
            if (climbing != null) climbing.clientTick();
        }
    }
}
