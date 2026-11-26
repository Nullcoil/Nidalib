package net.nullcoil.nidalib.client.climbing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Added to LivingEntityRenderState by mixin: how a WallClimber's model is turned onto its surface. */
@Environment(EnvType.CLIENT)
public interface WallClimberRenderState {
    /** Whether the model is turned at all this frame. */
    boolean nidalib$isClimbRotated();

    void nidalib$setClimbRotated(boolean rotated);

    /** Rotation that turns the model's feet toward its surface. */
    Quaternionf nidalib$getClimbRotation();

    /** Where its feet go, relative to the entity's position (world units, before entity scale). */
    Vector3f nidalib$getClimbOffset();
}
