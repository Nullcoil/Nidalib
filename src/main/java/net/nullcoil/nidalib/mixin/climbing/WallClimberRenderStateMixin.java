package net.nullcoil.nidalib.mixin.climbing;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.nullcoil.nidalib.client.climbing.WallClimberRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class WallClimberRenderStateMixin implements WallClimberRenderState {
    @Unique private boolean nidalib$climbRotated;
    @Unique private final Quaternionf nidalib$climbRotation = new Quaternionf();
    @Unique private final Vector3f nidalib$climbOffset = new Vector3f();

    @Override
    public boolean nidalib$isClimbRotated() {
        return nidalib$climbRotated;
    }

    @Override
    public void nidalib$setClimbRotated(boolean rotated) {
        nidalib$climbRotated = rotated;
    }

    @Override
    public Quaternionf nidalib$getClimbRotation() {
        return nidalib$climbRotation;
    }

    @Override
    public Vector3f nidalib$getClimbOffset() {
        return nidalib$climbOffset;
    }
}
