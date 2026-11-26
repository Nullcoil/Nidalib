package net.nullcoil.nidalib.climbing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

/**
 * Navigation for {@link WallClimber}s: paths go over floors, up walls and (if allowed) across
 * ceilings, and it keeps following them while it's off the ground.
 *
 * <p>Unlike vanilla's {@code WallClimberNavigation} (spiders), which paths along the ground and
 * just climbs whatever it bumps into, this one plans the climb. It extends
 * {@code PathNavigation} directly because {@code GroundPathNavigation} moves every target down
 * to the ground below it, which would make spots on walls unreachable.
 */
public class WallClimberNavigation extends PathNavigation {
    private final WallClimber climber;

    public WallClimberNavigation(Mob mob, Level level) {
        super(mob, level);
        if (!(mob instanceof WallClimber climber)) {
            throw new IllegalArgumentException(mob.getClass().getName() + " must implement WallClimber");
        }
        this.climber = climber;
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new WallClimbNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private boolean isClimbing() {
        WallClimbing climbing = this.climber.getWallClimbing();
        return climbing != null && climbing.isClimbing();
    }

    @Override
    protected Vec3 getTempMobPos() {
        return this.mob.position();
    }

    @Override
    protected boolean canUpdatePath() {
        return this.mob.onGround() || this.isClimbing() || this.mob.isInWater() || this.mob.isInLava() || this.mob.isPassenger();
    }

    /** Random-stroll goals only pick "stable" spots: spots on climbable faces count too. */
    @Override
    public boolean isStableDestination(BlockPos pos) {
        if (super.isStableDestination(pos)) return true;
        if (!this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty()) return false;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN || !this.climber.canClimbOn(dir)) continue;
            BlockPos side = pos.relative(dir);
            if (this.level.getBlockState(side).isFaceSturdy(this.level, side, dir.getOpposite())) return true;
        }
        return false;
    }

    @Override
    protected void followThePath() {
        if (!this.isClimbing()) {
            super.followThePath();
            return;
        }
        Path path = this.path;
        if (path == null) return;

        // Vanilla only checks horizontal distance. On a wall, move on once its middle is inside
        // the next spot, looking a couple of spots ahead so overshooting one doesn't turn it back
        Vec3 center = this.mob.getBoundingBox().getCenter();
        int next = path.getNextNodeIndex();
        int last = Math.min(next + 2, path.getNodeCount() - 1);
        for (int i = last; i >= next; i--) {
            if (this.isInside(path.getNode(i), center)) {
                path.setNextNodeIndex(i + 1);
                break;
            }
        }
        this.doStuckDetection(this.getTempMobPos());
    }

    private boolean isInside(Node node, Vec3 point) {
        int w = Mth.floor(this.mob.getBbWidth() + 1.0F);
        int h = Mth.floor(this.mob.getBbHeight() + 1.0F);
        return point.x >= node.x && point.x <= node.x + w
                && point.y >= node.y && point.y <= node.y + h
                && point.z >= node.z && point.z <= node.z + w;
    }
}
