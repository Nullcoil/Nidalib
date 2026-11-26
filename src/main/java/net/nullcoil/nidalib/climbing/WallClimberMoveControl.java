package net.nullcoil.nidalib.climbing;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Move control for {@link WallClimber}s. On the floor it's vanilla's. On a wall or ceiling it
 * moves straight along the surface toward where it wants to go, pressed against it so it
 * stays attached, and turns to face its way in the surface's frame.
 *
 * <p>It also picks the surface every tick ({@link WallClimbing#serverTick}). A mob with its own
 * move control should extend this one and call {@code super.tick()}, or call {@code serverTick}
 * itself.
 */
public class WallClimberMoveControl<T extends Mob & WallClimber> extends MoveControl<T> {
    /** How hard it presses into its surface (blocks per tick). */
    private static final double STICK = 0.08;
    /** Share of the gap to its target velocity closed each tick. */
    private static final double ACCELERATION = 0.6;
    /** Most it turns per tick (degrees). */
    private static final float MAX_TURN = 30.0F;

    public WallClimberMoveControl(T mob) {
        super(mob);
    }

    @Override
    public void tick() {
        WallClimbing climbing = this.mob.getWallClimbing();
        boolean moving = this.operation == Operation.MOVE_TO;
        Vec3 want = moving ? new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ()) : null;

        climbing.serverTick(want);
        if (!climbing.isClimbing()) {
            super.tick();
            return;
        }

        // Climbing: no walking input, velocity is set directly
        this.operation = Operation.WAIT;
        this.mob.setSpeed(0.0F);
        this.mob.setXxa(0.0F);
        this.mob.setYya(0.0F);
        this.mob.setZza(0.0F);

        Vec3 up = climbing.getUp();
        Vec3 velocity = Vec3.ZERO;
        if (want != null) {
            // Along the surface while touching it; straight at the target while swinging over an edge
            Vec3 dir = climbing.isTouching() ? want.subtract(up.scale(want.dot(up))) : want;
            double distance = dir.length();
            if (distance > 1.0E-3) {
                double speed = this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * this.mob.getClimbSpeedFactor();
                velocity = dir.scale(Math.min(speed, distance) / distance);

                Float yaw = WallClimbing.localYaw(climbing.getSurface(), dir);
                if (yaw != null) {
                    float turned = this.rotlerp(this.mob.getYRot(), yaw, MAX_TURN);
                    this.mob.setYRot(turned);
                    this.mob.setYBodyRot(turned);
                }
            }
        }
        if (climbing.isTouching()) {
            velocity = velocity.subtract(up.scale(STICK));
        }

        Vec3 current = this.mob.getDeltaMovement();
        this.mob.setDeltaMovement(current.add(velocity.subtract(current).scale(ACCELERATION)));
    }
}
