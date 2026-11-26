package net.nullcoil.nidalib.mixin.climbing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.nullcoil.nidalib.climbing.WallClimber;
import net.nullcoil.nidalib.climbing.WallClimbing;
import net.nullcoil.nidalib.client.climbing.WallClimberRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class WallClimberRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void nidalib$extractClimbPose(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        WallClimberRenderState climbState = (WallClimberRenderState) state;
        WallClimbing climbing = entity instanceof WallClimber climber ? climber.getWallClimbing() : null;
        boolean rotated = climbing != null && climbing.hasRenderRotation();
        climbState.nidalib$setClimbRotated(rotated);
        if (rotated) {
            climbing.getRenderPose(partialTick, climbState.nidalib$getClimbRotation(), climbState.nidalib$getClimbOffset());
        }
    }

    /**
     * Before vanilla's body yaw: move the pivot to the face it's standing on and turn the model
     * so that face is its floor. Vanilla's yaw (and anything a subclass adds after calling
     * super) then happens in the surface's frame.
     */
    @Inject(
            method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD")
    )
    private void nidalib$rotateOntoSurface(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        WallClimberRenderState climbState = (WallClimberRenderState) state;
        if (!climbState.nidalib$isClimbRotated()) return;
        // The pose is already scaled by the entity's scale here; the offset is in world units
        float inv = entityScale != 0.0F ? 1.0F / entityScale : 1.0F;
        var offset = climbState.nidalib$getClimbOffset();
        poseStack.translate(offset.x * inv, offset.y * inv, offset.z * inv);
        poseStack.mulPose(climbState.nidalib$getClimbRotation());
    }
}
