package net.nullcoil.nidalib.climbing;

import net.minecraft.core.Direction;

/**
 * A mob that can walk on walls (and optionally ceilings) as if they were the floor.
 *
 * <p>Setup, on a {@code Mob} subclass:
 * <pre>{@code
 * public class WrethEntity extends Monster implements WallClimber {
 *     private static final EntityDataAccessor<Direction> DATA_SURFACE = WallClimbing.defineSurface(WrethEntity.class);
 *     private final WallClimbing climbing = new WallClimbing(this, DATA_SURFACE);
 *
 *     public WrethEntity(EntityType<? extends WrethEntity> type, Level level) {
 *         super(type, level);
 *         this.moveControl = new WallClimberMoveControl<>(this);
 *     }
 *
 *     @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
 *         super.defineSynchedData(builder);
 *         WallClimbing.define(builder, DATA_SURFACE);
 *     }
 *
 *     @Override protected PathNavigation createNavigation(Level level) {
 *         return new WallClimberNavigation(this, level);
 *     }
 *
 *     @Override public WallClimbing getWallClimbing() { return climbing; }
 *     @Override public boolean canClimbOn(Direction face) { return face != Direction.DOWN; } // walls + ceilings
 * }
 * }</pre>
 *
 * <p>Nidalib handles the rest: gravity is off while it's on a wall or ceiling, it takes no
 * fall damage there, and the renderer turns the model so the surface it's on is its floor.
 */
public interface WallClimber {
    /** The climbing state this mob owns (create one per mob, see the class docs). */
    WallClimbing getWallClimbing();

    /**
     * Whether it can stick to the block face in this direction from itself: {@code NORTH} means
     * a wall to its north, {@code UP} a ceiling. {@code DOWN} (the floor) is always allowed and
     * never asked about. Default: walls only, like a spider.
     */
    default boolean canClimbOn(Direction face) {
        return face.getAxis().isHorizontal();
    }

    /** Climbing speed as a share of its movement speed attribute (blocks per tick). */
    default double getClimbSpeedFactor() {
        return 0.5;
    }

    /** Extra path cost per climbing node, so it prefers floors when the climb isn't shorter. 0 = no preference. */
    default float getClimbPathCost() {
        return 0.0F;
    }
}
