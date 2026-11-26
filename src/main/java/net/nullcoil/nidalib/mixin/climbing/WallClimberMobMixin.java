package net.nullcoil.nidalib.mixin.climbing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.nullcoil.nidalib.climbing.WallClimberMoveControl;
import net.nullcoil.nidalib.climbing.WallClimberNavigation;
import net.nullcoil.nidalib.climbing.WallClimbers;
import net.nullcoil.nidalib.climbing.WallClimbing;
import net.nullcoil.nidalib.climbing.WallClimbingHolder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wall climbing for any mob: the synced face it stands on (defined for every mob, since synced
 * data IDs are per class; it's one byte and only sent when it isn't the floor), plus the
 * climbing navigation and move control for mobs that can climb.
 */
@Mixin(Mob.class)
public abstract class WallClimberMobMixin implements WallClimbingHolder {
    @Unique
    private static final EntityDataAccessor<Direction> NIDALIB$DATA_SURFACE =
            SynchedEntityData.defineId(Mob.class, EntityDataSerializers.DIRECTION);

    @Unique
    private static final String NIDALIB$SURFACE_KEY = "nidalib:climb_surface";

    @Shadow protected MoveControl<?> moveControl;

    @Unique private @Nullable WallClimbing nidalib$climbing;
    @Unique private boolean nidalib$climbingChecked;

    @Override
    public @Nullable WallClimbing nidalib$getWallClimbing() {
        if (!this.nidalib$climbingChecked) {
            this.nidalib$climbingChecked = true;
            Mob self = (Mob) (Object) this;
            if (WallClimbers.canClimb(self)) {
                this.nidalib$climbing = new WallClimbing(self, NIDALIB$DATA_SURFACE);
            }
        }
        return this.nidalib$climbing;
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void nidalib$defineSurface(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(NIDALIB$DATA_SURFACE, Direction.DOWN);
    }

    /** Swapped in before its goals are registered, so goals that set up the navigation (FloatGoal) set up this one. */
    @ModifyExpressionValue(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;createNavigation(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/ai/navigation/PathNavigation;")
    )
    private PathNavigation nidalib$climbingNavigation(PathNavigation original) {
        Mob self = (Mob) (Object) this;
        if (original instanceof WallClimberNavigation || !WallClimbers.canClimb(self)) return original;
        return new WallClimberNavigation(self, self.level());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nidalib$climbingMoveControl(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!(this.moveControl instanceof WallClimberMoveControl) && WallClimbers.canClimb(self)) {
            this.moveControl = new WallClimberMoveControl<>(self);
        }
    }

    /** Saved so it reloads on its wall with its box turned, instead of an upright box half inside the wall. */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void nidalib$saveSurface(ValueOutput output, CallbackInfo ci) {
        WallClimbing climbing = this.nidalib$getWallClimbing();
        if (climbing != null && climbing.isClimbing()) {
            output.putByte(NIDALIB$SURFACE_KEY, (byte) climbing.getSurface().get3DDataValue());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void nidalib$loadSurface(ValueInput input, CallbackInfo ci) {
        WallClimbing climbing = this.nidalib$getWallClimbing();
        if (climbing != null) {
            climbing.restoreSurface(Direction.from3DDataValue(input.getByteOr(NIDALIB$SURFACE_KEY, (byte) Direction.DOWN.get3DDataValue())));
        }
    }

    /** After the look control: aim the head in the surface's frame instead of the world's. */
    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void nidalib$alignClimbingHead(CallbackInfo ci) {
        WallClimbing climbing = this.nidalib$getWallClimbing();
        if (climbing != null) climbing.alignHead();
    }
}
