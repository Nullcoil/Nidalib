package net.nullcoil.nidalib.climbing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Walking pathfinding, plus nodes on walls (and ceilings, if the mob allows them).
 *
 * <p>Floor nodes are vanilla's. On top of those, from any node it can step one block in each of
 * the six directions to an open spot that touches a face it can climb. Spots that only touch a
 * climbable block at an edge or corner (just over the top of a wall, round an outer corner) are
 * allowed one step out from a supported spot, so paths can crest walls and wrap round pillars.
 */
public class WallClimbNodeEvaluator extends WalkNodeEvaluator {
    /** Extra cost of an edge spot, so paths don't hug edges when they don't need to. */
    private static final float EDGE_COST = 1.0F;

    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    @Override
    public Node getStart() {
        // Vanilla drops the start to the ground below; on a wall, start where it is
        WallClimbing climbing = WallClimbing.get(this.mob);
        if (climbing != null && climbing.isClimbing()) {
            int x = Mth.floor(this.mob.getX() - this.entityWidth * 0.5 + 0.5);
            int y = Mth.floor(this.mob.getY() + 0.1);
            int z = Mth.floor(this.mob.getZ() - this.entityDepth * 0.5 + 0.5);
            Node node = this.getNode(x, y, z);
            node.type = this.getCachedPathType(x, y, z);
            node.costMalus = this.mob.getPathfindingMalus(node.type);
            return node;
        }
        return super.getStart();
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node node) {
        int count = super.getNeighbors(neighbors, node);
        ClimbSettings climber = WallClimbers.get(this.mob);
        if (climber == null) return count;

        boolean fromSupported = node.type == PathType.WALKABLE || this.isSupported(climber, node.x, node.y, node.z);
        for (Direction dir : Direction.values()) {
            if (count >= neighbors.length) break;
            Node next = this.climbNode(climber, node.x + dir.getStepX(), node.y + dir.getStepY(), node.z + dir.getStepZ(), fromSupported);
            if (next != null && !contains(neighbors, count, next)) {
                neighbors[count++] = next;
            }
        }
        return count;
    }

    private @Nullable Node climbNode(ClimbSettings climber, int x, int y, int z, boolean fromSupported) {
        PathType type = this.getCachedPathType(x, y, z);
        float malus = this.mob.getPathfindingMalus(type);
        if (malus < 0.0F) return null;

        boolean supported = type == PathType.WALKABLE || this.isSupported(climber, x, y, z);
        if (!supported && !(fromSupported && this.isNearSupport(climber, x, y, z))) return null;

        Node node = this.getNode(x, y, z);
        if (node.closed) return null;
        node.type = type;
        float cost = malus + (type == PathType.WALKABLE ? 0.0F : climber.pathCost()) + (supported ? 0.0F : EDGE_COST);
        node.costMalus = Math.max(node.costMalus, cost);
        return node;
    }

    private static boolean contains(Node[] nodes, int count, Node node) {
        for (int i = 0; i < count; i++) {
            if (nodes[i] == node) return true;
        }
        return false;
    }

    /** Whether the mob's footprint at this node is right against a face it can climb, with room for its turned box there. */
    private boolean isSupported(ClimbSettings climber, int x, int y, int z) {
        int w = this.entityWidth, h = this.entityHeight, d = this.entityDepth;
        if (climber.canClimbOn(Direction.NORTH) && this.anySolid(x, x + w - 1, y, y + h - 1, z - 1, z - 1) && this.fits(Direction.NORTH, x, y, z)) return true;
        if (climber.canClimbOn(Direction.SOUTH) && this.anySolid(x, x + w - 1, y, y + h - 1, z + d, z + d) && this.fits(Direction.SOUTH, x, y, z)) return true;
        if (climber.canClimbOn(Direction.WEST) && this.anySolid(x - 1, x - 1, y, y + h - 1, z, z + d - 1) && this.fits(Direction.WEST, x, y, z)) return true;
        if (climber.canClimbOn(Direction.EAST) && this.anySolid(x + w, x + w, y, y + h - 1, z, z + d - 1) && this.fits(Direction.EAST, x, y, z)) return true;
        return climber.canClimbOn(Direction.UP) && this.anySolid(x, x + w - 1, y + h, y + h, z, z + d - 1);
    }

    /**
     * Whether its box, turned onto a wall on that side (what its adaptive box grows toward there,
     * facing up or down the wall), fits at this node: flush against the wall, from the bottom of
     * the node up, centered across the node's footprint. (Ceilings use vanilla's checks: turned
     * upside down its box is the same size.)
     */
    private boolean fits(Direction wall, int x, int y, int z) {
        double w = this.mob.getBbWidth();
        double h = this.mob.getBbHeight();
        double cx = x + this.entityWidth * 0.5;
        double cz = z + this.entityDepth * 0.5;
        AABB box = switch (wall) {
            case NORTH -> new AABB(cx - w / 2, y, z, cx + w / 2, y + w, z + h);
            case SOUTH -> new AABB(cx - w / 2, y, z + this.entityDepth - h, cx + w / 2, y + w, z + this.entityDepth);
            case WEST -> new AABB(x, y, cz - w / 2, x + h, y + w, cz + w / 2);
            case EAST -> new AABB(x + this.entityWidth - h, y, cz - w / 2, x + this.entityWidth, y + w, cz + w / 2);
            default -> null;
        };
        return box == null || this.currentContext.level().noCollision(box.deflate(1.0E-5));
    }

    /** Whether anything solid is diagonally next to the footprint (edges and corners). */
    private boolean isNearSupport(ClimbSettings climber, int x, int y, int z) {
        int w = this.entityWidth, h = this.entityHeight, d = this.entityDepth;
        int top = climber.canClimbOn(Direction.UP) ? y + h : y + h - 1;
        return this.anySolid(x - 1, x + w, y - 1, top, z - 1, z + d);
    }

    private boolean anySolid(int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    this.scratch.set(x, y, z);
                    BlockState state = this.currentContext.getBlockState(this.scratch);
                    if (!state.getCollisionShape(this.currentContext.level(), this.scratch).isEmpty()) return true;
                }
            }
        }
        return false;
    }
}
