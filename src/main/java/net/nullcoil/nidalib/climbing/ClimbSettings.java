package net.nullcoil.nidalib.climbing;

import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.Set;

/**
 * How a mob climbs: which faces it can stand on, how fast it climbs, and how much its paths
 * avoid climbing.
 *
 * <pre>{@code
 * ClimbSettings.builder().ceilings(true).speedFactor(1.0).build()
 * }</pre>
 *
 * @param faces       faces it can stand on besides the floor ({@code NORTH} = a wall to its north, {@code UP} = a ceiling)
 * @param speedFactor climbing speed as a share of its movement speed attribute (blocks per tick)
 * @param pathCost    extra path cost per climbing spot, so it prefers floors when the climb isn't shorter (0 = no preference)
 * @param stuckTicks  how long it can try to move on a wall or ceiling without getting anywhere before it lets go (0 = never)
 * @param tilt        whether the model leans to follow uneven ground (steps, slopes, edges) on whatever face it's on
 */
public record ClimbSettings(Set<Direction> faces, double speedFactor, float pathCost, int stuckTicks, boolean tilt) {
    /** Walls only, half its walking speed: like a spider. */
    public static final ClimbSettings WALLS = builder().build();
    /** Walls and ceilings, half its walking speed. */
    public static final ClimbSettings WALLS_AND_CEILINGS = builder().ceilings(true).build();

    public ClimbSettings {
        EnumSet<Direction> copy = EnumSet.noneOf(Direction.class);
        copy.addAll(faces);
        copy.remove(Direction.DOWN);
        faces = Set.copyOf(copy);
    }

    /** Whether it can stand on the face in this direction from itself. The floor ({@code DOWN}) always counts. */
    public boolean canClimbOn(Direction face) {
        return face == Direction.DOWN || this.faces.contains(face);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean walls = true;
        private boolean ceilings = false;
        private double speedFactor = 0.5;
        private float pathCost = 0.0F;
        private int stuckTicks = 40;
        private boolean tilt = true;

        private Builder() {
        }

        /** Walls (default on). */
        public Builder walls(boolean walls) {
            this.walls = walls;
            return this;
        }

        /** Ceilings (default off). */
        public Builder ceilings(boolean ceilings) {
            this.ceilings = ceilings;
            return this;
        }

        /** Climbing speed as a share of its movement speed attribute (default 0.5). */
        public Builder speedFactor(double speedFactor) {
            this.speedFactor = speedFactor;
            return this;
        }

        /** Extra path cost per climbing spot (default 0). */
        public Builder pathCost(float pathCost) {
            this.pathCost = pathCost;
            return this;
        }

        /** Ticks without progress on a wall or ceiling before it lets go and drops (default 40, 2 seconds; 0 = never). */
        public Builder stuckTicks(int stuckTicks) {
            this.stuckTicks = stuckTicks;
            return this;
        }

        /** Lean the model, and the box sized around it, to follow uneven ground (default on). */
        public Builder tilt(boolean tilt) {
            this.tilt = tilt;
            return this;
        }

        public ClimbSettings build() {
            EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
            if (this.walls) {
                for (Direction d : Direction.Plane.HORIZONTAL) faces.add(d);
            }
            if (this.ceilings) faces.add(Direction.UP);
            return new ClimbSettings(faces, this.speedFactor, this.pathCost, this.stuckTicks, this.tilt);
        }
    }
}
