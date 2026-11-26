package net.nullcoil.nidalib.climbing;

/**
 * Optional: implement on your own mob class to make it climb, instead of registering its type
 * with {@link WallClimbers#register}. Useful when the settings change at runtime (a mob that
 * loses its grip on ceilings after being hit, say): this is asked every time.
 *
 * <p>Nothing else is needed. Nidalib swaps in {@link WallClimberNavigation} and
 * {@link WallClimberMoveControl}, syncs which face it's on, turns off gravity while it's on a
 * wall or ceiling, turns the model and hitbox so that face is its floor, lets go when it's
 * stuck, and never gives it fall damage. Its state is at {@link WallClimbing#get}.
 */
public interface WallClimber {
    default ClimbSettings getClimbSettings() {
        return ClimbSettings.WALLS;
    }
}
