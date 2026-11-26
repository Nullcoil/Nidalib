package net.nullcoil.nidalib.mixin.climbing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nullcoil.nidalib.climbing.WallClimbing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class WallClimberEntityMixin {
    @Shadow public abstract Vec3 position();
    @Shadow public abstract void setBoundingBox(AABB box);
    @Shadow protected abstract AABB makeBoundingBox(Vec3 pos);
    @Shadow public abstract Level level();
    @Shadow public double xo;
    @Shadow public double yo;
    @Shadow public double zo;
    @Shadow public boolean noPhysics;

    /** No gravity while it's on a wall or ceiling. */
    @Inject(method = "getGravity", at = @At("HEAD"), cancellable = true)
    private void nidalib$noGravityWhileClimbing(CallbackInfoReturnable<Double> cir) {
        WallClimbing climbing = WallClimbing.get((Entity) (Object) this);
        if (climbing != null && climbing.isClimbing()) cir.setReturnValue(0.0);
    }

    /** On a wall its box is turned with it (see {@link WallClimbing#getExtents}). */
    @Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void nidalib$turnedBoundingBox(Vec3 pos, CallbackInfoReturnable<AABB> cir) {
        WallClimbing climbing = WallClimbing.get((Entity) (Object) this);
        if (climbing != null && climbing.isClimbing()) cir.setReturnValue(climbing.makeBoundingBox(pos));
    }

    // ---- Eyes: out from the face it's on, not straight up (see WallClimbing#getEyePosition)

    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void nidalib$turnedEyePosition(CallbackInfoReturnable<Vec3> cir) {
        WallClimbing climbing = WallClimbing.get((Entity) (Object) this);
        if (climbing != null && climbing.isClimbing()) cir.setReturnValue(climbing.getEyePosition(this.position()));
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void nidalib$turnedEyePositionLerped(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        WallClimbing climbing = WallClimbing.get((Entity) (Object) this);
        if (climbing != null && climbing.isClimbing()) {
            Vec3 pos = this.position();
            Vec3 lerped = new Vec3(Mth.lerp(partialTick, this.xo, pos.x), Mth.lerp(partialTick, this.yo, pos.y), Mth.lerp(partialTick, this.zo, pos.z));
            cir.setReturnValue(climbing.getEyePosition(lerped));
        }
    }

    @Inject(method = "getEyeY", at = @At("HEAD"), cancellable = true)
    private void nidalib$turnedEyeY(CallbackInfoReturnable<Double> cir) {
        WallClimbing climbing = WallClimbing.get((Entity) (Object) this);
        if (climbing != null && climbing.isClimbing()) cir.setReturnValue(climbing.getEyePosition(this.position()).y);
    }

    /**
     * Suffocation still applies to climbers, but checked with the square turned to lie along the
     * face it's on, at its turned eyes. Same test as vanilla otherwise: a suffocating block whose
     * collision shape overlaps the square.
     */
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void nidalib$turnedInWall(CallbackInfoReturnable<Boolean> cir) {
        WallClimbing climbing = WallClimbing.get((Entity) (Object) this);
        if (climbing == null || !climbing.isClimbing() || this.noPhysics) return;
        AABB check = climbing.getInWallCheckBox(climbing.getEyePosition(this.position()));
        Level level = this.level();
        VoxelShape checkShape = Shapes.create(check);
        cir.setReturnValue(BlockPos.betweenClosedStream(check).anyMatch(pos -> {
            BlockState state = level.getBlockState(pos);
            return !state.isAir()
                    && state.isSuffocating(level, pos)
                    && Shapes.joinIsNotEmpty(state.getCollisionShape(level, pos).move(pos.getX(), pos.getY(), pos.getZ()), checkShape, BooleanOp.AND);
        }));
    }

    /** The surface changed (on the client, from the server): rebuild the box in its new shape. */
    @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
    private void nidalib$refreshTurnedBox(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        WallClimbing climbing = WallClimbing.get((Entity) (Object) this);
        if (climbing != null && climbing.isSurfaceData(accessor)) {
            this.setBoundingBox(this.makeBoundingBox(this.position()));
        }
    }
}
