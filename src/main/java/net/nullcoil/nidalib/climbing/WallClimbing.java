package net.nullcoil.nidalib.climbing;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Climbing state for one mob that can climb (see {@link WallClimbers}): which block face it's standing on (synced to
 * clients), how it picks a new one, its adaptive box, and (client side) the smoothed, tilted model rotation.
 *
 * <p>The "surface" is the direction from the mob to the face it stands on: {@code DOWN} is the
 * floor (normal walking), {@code NORTH} a wall to its north, {@code UP} a ceiling.
 *
 * <p><b>Adaptive box.</b> Collision has to be an axis-aligned box, so its box is the smallest
 * axis-aligned box around its normal box turned onto its surface and pitched by the ground
 * under it ({@link #getTargetExtents}); yaw and sideways lean are left out. Pitched, that box
 * is bigger than the normal one. Every tick its box changes toward that size by at
 * most {@link #MORPH} per axis: shrinking always happens (the smaller box sits inside the old
 * one), growing only where the bigger box fits, so it never pushes itself into a block. Switching
 * surfaces doesn't touch the box at all. The size is synced to clients, so both sides collide
 * with the same box.
 *
 * <p>Its yaw ({@code yRot}, {@code yBodyRot}, {@code yHeadRot}) is measured in the surface's own
 * frame while it's climbing: the renderer first turns the model so its feet face the surface,
 * then applies yaw as usual. {@link #localYaw} and {@link #forward} convert between the two.
 */
public class WallClimbing {
    /** How far from a face it can be and still grab it. */
    private static final double REACH = 0.3;
    /** Probes are shrunk this much at the edges, so blocks it only grazes don't count. */
    private static final double EDGE = 0.01;
    /** How directly it has to be heading into another face to step onto it (cosine). */
    private static final double STEP_ONTO = 0.5;
    /** Ticks it keeps holding on after losing its face, while following a path (edges, outer corners). */
    private static final int HOLD_PATHING = 10;
    /** Same, while idle. */
    private static final int HOLD_IDLE = 2;
    /** It has to get this far (squared, so half a block) from where it started trying, or it's making no progress. */
    private static final double STUCK_PROGRESS_SQR = 0.25;
    /** After letting go because it was stuck, it can't grab anything for this long, so it actually drops. */
    private static final int STUCK_NO_GRAB = 30;
    /** Share of the way the model turns toward its target rotation each tick (client). */
    private static final float TURN = 0.35F;

    /** Box: most each side length changes per tick (blocks). */
    private static final double MORPH = 0.1;
    /** Box: changes smaller than this aren't worth a resync. */
    private static final double MORPH_EPSILON = 1.0E-3;

    /** Head: degrees per tick it turns toward what it's looking at (vanilla's default). */
    private static final float HEAD_TURN = 10.0F;
    /** Head: ticks it keeps looking at the last look target after goals stop asking (goals tick every other tick). */
    private static final int LOOK_HOLD = 3;

    /** Tilt: samples sit this share of its width out from its middle, on both sides. */
    private static final double TILT_SPREAD = 0.45;
    /** Tilt: a sample with nothing under it within this far below its feet counts as a drop this deep. */
    private static final double TILT_DROP = 1.0;
    /** Tilt: most it leans away from the face it's on. */
    private static final double MAX_TILT = Math.toRadians(45.0);

    private final Mob mob;
    private final EntityDataAccessor<Byte> surfaceData;
    private final EntityDataAccessor<Float> sizeX, sizeY, sizeZ;

    // Server
    private boolean touching = true;
    private int lostTicks;
    private int grabCooldown;
    private @Nullable Vec3 stuckAnchor;
    private int stuckTime;
    private boolean headInitialized;
    private float headYaw;
    private float headPitch;
    private int lookHold;
    private double lookX, lookY, lookZ;

    // Client
    private boolean initialized;
    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf prevRotation = new Quaternionf();
    private final Vector3f offset = new Vector3f();
    private final Vector3f prevOffset = new Vector3f();

    /** Made by Nidalib for each mob that can climb; get it with {@link #get}. */
    public WallClimbing(Mob mob, EntityDataAccessor<Byte> surfaceData,
                        EntityDataAccessor<Float> sizeX, EntityDataAccessor<Float> sizeY, EntityDataAccessor<Float> sizeZ) {
        this.mob = mob;
        this.surfaceData = surfaceData;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    /** The climbing state of this entity, or null if it can't climb. */
    public static @Nullable WallClimbing get(Entity entity) {
        return entity instanceof WallClimbingHolder holder ? holder.nidalib$getWallClimbing() : null;
    }

    /** The settings it climbs with right now. */
    public ClimbSettings getSettings() {
        ClimbSettings settings = WallClimbers.get(this.mob);
        return settings != null ? settings : ClimbSettings.WALLS;
    }

    /** The smallest its box ever gets on any axis: the smaller of its width and height (any turn of its box is at least this wide). */
    public static double climbBoxSize(Entity entity) {
        return Math.min(entity.getBbWidth(), entity.getBbHeight());
    }

    // ---------------------------------------------------------------- state

    /** The face it's standing on ({@code DOWN} = floor). */
    public Direction getSurface() {
        // The box is first made inside Entity's constructor, before synced data exists
        SynchedEntityData entityData = this.mob.getEntityData();
        return entityData != null ? Direction.from3DDataValue(entityData.get(this.surfaceData) & 7) : Direction.DOWN;
    }

    /** Whether this is one of the synced values that shape its box (surface or size). */
    public boolean isSurfaceData(EntityDataAccessor<?> accessor) {
        return accessor == this.surfaceData || accessor == this.sizeX || accessor == this.sizeY || accessor == this.sizeZ;
    }

    /** On a wall or ceiling (not the floor). */
    public boolean isClimbing() {
        return this.getSurface() != Direction.DOWN;
    }

    public boolean isOnCeiling() {
        return this.getSurface() == Direction.UP;
    }

    /** Server: whether it's actually touching its surface this tick (false while holding on past an edge). */
    public boolean isTouching() {
        return this.touching;
    }

    /** Unit vector pointing away from its surface: its "up". */
    public Vec3 getUp() {
        Direction s = this.getSurface();
        return new Vec3(-s.getStepX(), -s.getStepY(), -s.getStepZ());
    }

    /** Lets go: back to the floor with gravity, and no grabbing anything for this many ticks (e.g. dropping on a player). */
    public void detach(int noGrabTicks) {
        this.grabCooldown = Math.max(this.grabCooldown, noGrabTicks);
        this.setSurface(Direction.DOWN);
    }

    // ---------------------------------------------------------------- box

    /** Whether its box has been sized yet (until then it's vanilla's). */
    public boolean hasAdaptiveBox() {
        SynchedEntityData entityData = this.mob.getEntityData();
        return entityData != null && entityData.get(this.sizeX) > 0.0F;
    }

    /** Size of its box right now. */
    public Vec3 getExtents() {
        if (!this.hasAdaptiveBox()) return this.normalExtents();
        SynchedEntityData d = this.mob.getEntityData();
        return new Vec3(d.get(this.sizeX), d.get(this.sizeY), d.get(this.sizeZ));
    }

    private Vec3 normalExtents() {
        return new Vec3(this.mob.getBbWidth(), this.mob.getBbHeight(), this.mob.getBbWidth());
    }

    private void setExtents(Vec3 e) {
        SynchedEntityData d = this.mob.getEntityData();
        d.set(this.sizeX, (float) e.x);
        d.set(this.sizeY, (float) e.y);
        d.set(this.sizeZ, (float) e.z);
    }

    /** Its box at this position (bottom center, as always). */
    public AABB makeBoundingBox(Vec3 pos) {
        Vec3 e = this.getExtents();
        return new AABB(pos.x - e.x / 2, pos.y, pos.z - e.z / 2, pos.x + e.x / 2, pos.y + e.y, pos.z + e.z / 2);
    }

    /** How the model is turned (before vanilla's yaw): onto its surface, then leaned to {@code normal}. */
    private static Quaternionf modelRotation(Direction surface, Vec3 up, Vec3 normal, Quaternionf dest) {
        return dest.rotationTo((float) up.x, (float) up.y, (float) up.z, (float) normal.x, (float) normal.y, (float) normal.z)
                .mul(surfaceRotation(surface, new Quaternionf()));
    }

    /**
     * The size it's growing or shrinking toward: its normal box turned onto its surface, then
     * pitched by the ground under it (nose up or down along the way it faces), and the smallest
     * axis-aligned box around that. Only pitch counts: turning (yaw) and leaning sideways (roll)
     * don't change the box, so it doesn't swell every time it turns. Flat, it's exactly its
     * normal box turned onto the surface (on a wall: width along it, width up it, height out).
     *
     * <p>Pitched by θ, its depth out from the surface becomes {@code h cos θ + w sin θ} and its
     * length along the way it faces {@code w cos θ + h sin θ}. Its facing is rounded to whichever
     * of the surface's two axes it's closer to; the other keeps its width.
     */
    public Vec3 getTargetExtents() {
        Direction s = this.getSurface();
        Vec3 up = this.getUp();
        Vec3 n = this.groundNormal(s);
        Vec3 fwd = forward(s, this.mob.yBodyRot);

        double w = this.mob.getBbWidth();
        double h = this.mob.getBbHeight();
        double pitch = Math.atan2(Math.abs(n.dot(fwd)), Math.max(n.dot(up), 0.0));
        double sin = Math.sin(pitch);
        double cos = Math.cos(pitch);
        double depth = h * cos + w * sin;
        double length = w * cos + h * sin;

        Direction.Axis a = s.getAxis();
        Direction.Axis[] t = tangents(a);
        boolean facesFirst = Math.abs(fwd.get(t[0])) >= Math.abs(fwd.get(t[1]));
        Vec3 size = along(a, depth)
                .add(along(t[0], facesFirst ? length : w))
                .add(along(t[1], facesFirst ? w : length));
        return size;
    }

    /**
     * Server, every tick: moves its box's size toward {@link #getTargetExtents}, at most
     * {@link #MORPH} per axis. The side against its surface stays put; across the surface it
     * stays centered if it can, else it grows away from whatever's in the way. Anything that
     * would grow into a block doesn't grow (that axis waits); shrinking always happens.
     */
    private void morphBox() {
        Vec3 current = this.getExtents();
        Vec3 target = this.getTargetExtents();
        Vec3 next = new Vec3(
                current.x + Mth.clamp(target.x - current.x, -MORPH, MORPH),
                current.y + Mth.clamp(target.y - current.y, -MORPH, MORPH),
                current.z + Mth.clamp(target.z - current.z, -MORPH, MORPH));
        if (this.hasAdaptiveBox() && next.distanceToSqr(current) < MORPH_EPSILON * MORPH_EPSILON) return;

        // Try it all, then each growing axis on its own, then just the shrinking
        Vec3 shrinkOnly = new Vec3(Math.min(next.x, current.x), Math.min(next.y, current.y), Math.min(next.z, current.z));
        Vec3[] tries = {
                next,
                new Vec3(next.x, shrinkOnly.y, shrinkOnly.z),
                new Vec3(shrinkOnly.x, next.y, shrinkOnly.z),
                new Vec3(shrinkOnly.x, shrinkOnly.y, next.z),
        };
        Vec3 bestSize = null;
        AABB bestBox = null;
        for (Vec3 size : tries) {
            AABB box = this.place(size);
            if (box != null) {
                bestSize = size;
                bestBox = box;
                break;
            }
        }
        if (bestBox == null) {
            bestSize = shrinkOnly;
            bestBox = this.placed(shrinkOnly, this.surfaceAnchors(0, 0));
        }
        if (this.hasAdaptiveBox() && bestSize.distanceToSqr(current) < MORPH_EPSILON * MORPH_EPSILON) return;
        this.setExtents(bestSize);
        this.mob.setPos(bottomCenter(bestBox));
    }

    /** A box of this size against its surface that fits, trying centered first, or null. */
    private @Nullable AABB place(Vec3 size) {
        int[] order = {0, -1, 1};
        for (int a : order) {
            for (int b : order) {
                AABB box = this.placed(size, this.surfaceAnchors(a, b));
                if (this.fits(box)) return box;
            }
        }
        return null;
    }

    /** Anchors: the surface's side on its axis; {@code a} and {@code b} (-1 min, 0 centered, 1 max) on the other two. */
    private int[] surfaceAnchors(int a, int b) {
        Direction s = this.getSurface();
        Direction.Axis[] t = tangents(s.getAxis());
        int[] anchors = new int[3];
        anchors[s.getAxis().ordinal()] = s.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        anchors[t[0].ordinal()] = a;
        anchors[t[1].ordinal()] = b;
        return anchors;
    }

    /**
     * A box of this size placed against the current one: on each axis it keeps the side given in
     * {@code anchors} (indexed by axis ordinal: -1 = min side, 1 = max side, 0 = centered).
     */
    private AABB placed(Vec3 size, int[] anchors) {
        AABB old = this.mob.getBoundingBox();
        double[] min = new double[3];
        double[] max = new double[3];
        Direction.Axis[] axes = Direction.Axis.values();
        for (int i = 0; i < 3; i++) {
            Direction.Axis axis = axes[i];
            double extent = size.get(axis);
            if (anchors[i] > 0) {
                max[i] = old.max(axis);
                min[i] = max[i] - extent;
            } else if (anchors[i] < 0) {
                min[i] = old.min(axis);
                max[i] = min[i] + extent;
            } else {
                double center = (old.min(axis) + old.max(axis)) / 2;
                min[i] = center - extent / 2;
                max[i] = center + extent / 2;
            }
        }
        return new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    private boolean fits(AABB box) {
        return this.mob.level().noCollision(this.mob, box.deflate(1.0E-5));
    }

    private static Vec3 bottomCenter(AABB box) {
        return new Vec3((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
    }

    /**
     * Where its eyes are when it stands at {@code pos} (bottom center of its box): out from the
     * middle of its box along its "up", by as much as vanilla's are above the middle of its
     * normal box, but always inside the box, so its eyes are never in a block unless its box is.
     * Used for everything that asks for its eye position (looking, line of sight, suffocation).
     */
    public Vec3 getEyePosition(Vec3 pos) {
        Vec3 e = this.getExtents();
        Vec3 up = this.getUp();
        double depth = Math.abs(up.x) * e.x + Math.abs(up.y) * e.y + Math.abs(up.z) * e.z;
        double lift = Mth.clamp(this.mob.getEyeHeight() - this.mob.getBbHeight() / 2, -depth * 0.4, depth * 0.4);
        return pos.add(0.0, e.y / 2, 0.0).add(up.scale(lift));
    }

    /**
     * Vanilla's suffocation check, turned: a flat square 0.8 × its box's narrower side across,
     * at its eyes, lying flat to the face it's on. It sits inside its box, so it only finds a
     * block when the box itself is in one (crushed, a block placed on it).
     */
    public AABB getInWallCheckBox(Vec3 eye) {
        Vec3 e = this.getExtents();
        double thin = 1.0E-6;
        return switch (this.getSurface().getAxis()) {
            case Y -> {
                double f = Math.min(e.x, e.z) * 0.8;
                yield AABB.ofSize(eye, f, thin, f);
            }
            case Z -> {
                double f = Math.min(e.x, e.y) * 0.8;
                yield AABB.ofSize(eye, f, f, thin);
            }
            case X -> {
                double f = Math.min(e.y, e.z) * 0.8;
                yield AABB.ofSize(eye, thin, f, f);
            }
        };
    }

    /** Loading: put it back on the face and at the size it was saved with, as it was (no turning, no moving). */
    public void restore(Direction surface, @Nullable Vec3 size) {
        if (surface != Direction.DOWN && !this.getSettings().canClimbOn(surface)) surface = Direction.DOWN;
        this.mob.getEntityData().set(this.surfaceData, (byte) surface.get3DDataValue());
        if (size != null && size.x > 0 && size.y > 0 && size.z > 0) this.setExtents(size);
    }

    private void setSurface(Direction surface) {
        Direction old = this.getSurface();
        if (old == surface) return;
        // Keep facing the same way across the switch. Walking straight into a wall, "forward"
        // points into the new surface; then it faces away from the old one (up the wall)
        Vec3 fwd = forward(old, this.mob.getYRot());
        Float yaw = localYaw(surface, fwd);
        if (yaw == null) {
            yaw = localYaw(surface, new Vec3(-old.getStepX(), -old.getStepY(), -old.getStepZ()));
        }
        this.mob.getEntityData().set(this.surfaceData, (byte) surface.get3DDataValue());
        if (yaw != null) {
            this.mob.setYRot(yaw);
            this.mob.setYBodyRot(yaw);
            this.mob.setYHeadRot(yaw);
        }
        this.headInitialized = false;
        this.lostTicks = 0;
    }

    // ---------------------------------------------------------------- server

    /**
     * Server, every tick (the {@link WallClimberMoveControl} calls this): picks the surface and
     * reshapes its box.
     *
     * @param want where it wants to go, relative to its position, or null if it isn't moving
     */
    public void serverTick(@Nullable Vec3 want) {
        if (this.grabCooldown > 0) this.grabCooldown--;

        Direction current = this.getSurface();
        EnumSet<Direction> contacts = this.grabCooldown > 0 ? EnumSet.noneOf(Direction.class) : this.findContacts();
        Direction next = this.choose(current, contacts, want);
        // The box doesn't change on a switch (it reshapes gradually below), so every switch works
        if (next != current) this.setSurface(next);

        this.touching = next == Direction.DOWN || contacts.contains(next);
        if (next != Direction.DOWN) this.mob.resetFallDistance();
        this.morphBox();
        this.checkStuck(want);
    }

    /**
     * Trying to move on a wall or ceiling but not getting anywhere (a path it can't follow, a
     * face it doesn't fit onto): let go, drop, and let its goals plan again from the ground.
     */
    private void checkStuck(@Nullable Vec3 want) {
        int limit = this.getSettings().stuckTicks();
        if (limit <= 0 || !this.isClimbing() || want == null || want.lengthSqr() < 1.0E-4) {
            this.stuckAnchor = null;
            this.stuckTime = 0;
            return;
        }
        Vec3 pos = this.mob.position();
        if (this.stuckAnchor == null || pos.distanceToSqr(this.stuckAnchor) > STUCK_PROGRESS_SQR) {
            this.stuckAnchor = pos;
            this.stuckTime = 0;
        } else if (++this.stuckTime >= limit) {
            this.stuckAnchor = null;
            this.stuckTime = 0;
            this.mob.getNavigation().stop();
            this.detach(STUCK_NO_GRAB);
        }
    }

    private Direction choose(Direction current, EnumSet<Direction> contacts, @Nullable Vec3 want) {
        boolean moving = want != null && want.lengthSqr() > 1.0E-4;

        if (moving) {
            Vec3 w = want.normalize();
            // Heading into another face it's touching: that face becomes its floor
            // (bottom of a wall onto the floor, wall into ceiling, inner corners)
            Direction best = null;
            double bestDot = STEP_ONTO;
            for (Direction d : contacts) {
                if (d == current) continue;
                // Only leave the floor for a wall when where it's going is above it
                if (current == Direction.DOWN && d.getAxis().isHorizontal() && want.y < 0.5) continue;
                double dot = w.x * d.getStepX() + w.y * d.getStepY() + w.z * d.getStepZ();
                if (dot > bestDot) {
                    best = d;
                    bestDot = dot;
                }
            }
            if (best != null) return best;

            // On the floor, path goes straight up: climb whichever wall it's next to
            if (current == Direction.DOWN && want.y > 0.5) {
                Direction wall = this.closestWall(contacts, w);
                if (wall != null) return wall;
            }
        }

        // At the foot of a wall and not heading up it: walk on the floor instead
        if (current != Direction.DOWN && contacts.contains(Direction.DOWN) && (!moving || want.y < 0.1)) {
            return Direction.DOWN;
        }

        if (contacts.contains(current)) {
            this.lostTicks = 0;
            return current;
        }

        if (current != Direction.DOWN) {
            // Lost its face: landed on top (cresting a wall), or came round an outer corner
            if (contacts.contains(Direction.DOWN)) return Direction.DOWN;
            Direction wall = this.closestWall(contacts, moving ? want.normalize() : Vec3.ZERO);
            if (wall != null) return wall;
            if (contacts.contains(Direction.UP)) return Direction.UP;
            // Nothing in reach: hold on a moment (lets it swing over edges), then fall
            if (++this.lostTicks <= (moving ? HOLD_PATHING : HOLD_IDLE)) return current;
            return Direction.DOWN;
        }

        // Falling past a wall (or loaded mid-climb): grab it, like a spider
        if (!this.mob.onGround() && !contacts.contains(Direction.DOWN)) {
            Direction wall = this.closestWall(contacts, Vec3.ZERO);
            if (wall != null) return wall;
        }
        return Direction.DOWN;
    }

    private @Nullable Direction closestWall(EnumSet<Direction> contacts, Vec3 heading) {
        Direction best = null;
        double bestDot = -2;
        for (Direction d : contacts) {
            if (!d.getAxis().isHorizontal()) continue;
            double dot = heading.x * d.getStepX() + heading.z * d.getStepZ();
            if (dot > bestDot) {
                best = d;
                bestDot = dot;
            }
        }
        return best;
    }

    private EnumSet<Direction> findContacts() {
        EnumSet<Direction> found = EnumSet.noneOf(Direction.class);
        AABB box = this.mob.getBoundingBox();
        ClimbSettings settings = this.getSettings();
        for (Direction d : Direction.values()) {
            if (!settings.canClimbOn(d)) continue;
            if (this.mob.level().getBlockCollisions(this.mob, probe(box, d, REACH)).iterator().hasNext()) {
                found.add(d);
            }
        }
        return found;
    }

    /** The thin slab just past one face of the box. */
    private static AABB probe(AABB b, Direction d, double reach) {
        AABB in = b.deflate(EDGE);
        return switch (d) {
            case DOWN -> new AABB(in.minX, b.minY - reach, in.minZ, in.maxX, b.minY, in.maxZ);
            case UP -> new AABB(in.minX, b.maxY, in.minZ, in.maxX, b.maxY + reach, in.maxZ);
            case NORTH -> new AABB(in.minX, in.minY, b.minZ - reach, in.maxX, in.maxY, b.minZ);
            case SOUTH -> new AABB(in.minX, in.minY, b.maxZ, in.maxX, in.maxY, b.maxZ + reach);
            case WEST -> new AABB(b.minX - reach, in.minY, in.minZ, b.minX, in.maxY, in.maxZ);
            case EAST -> new AABB(b.maxX, in.minY, in.minZ, b.maxX + reach, in.maxY, in.maxZ);
        };
    }

    /**
     * Server, after its look control ran: vanilla aims the head in world space, which is
     * meaningless on a wall. Re-aim it in the surface's frame instead, turning at vanilla's rate.
     *
     * <p>The head is kept here rather than read back from the mob, because vanilla's look control
     * has already turned it toward a world-space angle this tick. The look target is held for a
     * few ticks: goals like {@code LookAtPlayerGoal} only tick every other tick, and by the time
     * this runs the look control has already counted down the tick they set, so
     * {@code isLookingAtTarget()} is false every other tick.
     */
    public void alignHead() {
        Direction s = this.getSurface();
        if (s == Direction.DOWN) {
            this.headInitialized = false;
            this.lookHold = 0;
            return;
        }

        var look = this.mob.getLookControl();
        if (look.isLookingAtTarget()) {
            this.lookHold = LOOK_HOLD;
            this.lookX = look.getWantedX();
            this.lookY = look.getWantedY();
            this.lookZ = look.getWantedZ();
        } else if (this.lookHold > 0) {
            this.lookHold--;
        }

        float body = this.mob.yBodyRot;
        if (!this.headInitialized) {
            this.headInitialized = true;
            this.headYaw = body;
            this.headPitch = 0.0F;
        }

        float targetYaw = body;
        float targetPitch = 0.0F;
        if (this.lookHold > 0) {
            Vec3 eye = this.getEyePosition(this.mob.position());
            Vector3f local = toLocal(s, new Vec3(this.lookX - eye.x, this.lookY - eye.y, this.lookZ - eye.z));
            double horizontal = Math.sqrt(local.x * local.x + local.z * local.z);
            if (horizontal > 1.0E-4) {
                targetYaw = (float) (Mth.atan2(local.z, local.x) * Mth.RAD_TO_DEG) - 90.0F;
                float maxPitch = this.mob.getMaxHeadXRot();
                targetPitch = Mth.clamp((float) (-Mth.atan2(local.y, horizontal) * Mth.RAD_TO_DEG), -maxPitch, maxPitch);
            }
        }

        float maxYaw = this.mob.getMaxHeadYRot();
        float yaw = this.headYaw + Mth.clamp(Mth.wrapDegrees(targetYaw - this.headYaw), -HEAD_TURN, HEAD_TURN);
        this.headYaw = body + Mth.clamp(Mth.wrapDegrees(yaw - body), -maxYaw, maxYaw);
        this.headPitch += Mth.clamp(targetPitch - this.headPitch, -HEAD_TURN, HEAD_TURN);

        this.mob.setYHeadRot(this.headYaw);
        this.mob.setXRot(this.headPitch);
    }

    // ---------------------------------------------------------------- frames

    /** Rotation that turns the model's feet toward {@code surface}. */
    public static Quaternionf surfaceRotation(Direction surface, Quaternionf dest) {
        return switch (surface) {
            case DOWN -> dest.identity();
            case UP -> dest.rotationX(Mth.PI);
            case NORTH -> dest.rotationX(Mth.HALF_PI);
            case SOUTH -> dest.rotationX(-Mth.HALF_PI);
            case EAST -> dest.rotationZ(Mth.HALF_PI);
            case WEST -> dest.rotationZ(-Mth.HALF_PI);
        };
    }

    private static Vector3f toLocal(Direction surface, Vec3 world) {
        Vector3f v = new Vector3f((float) world.x, (float) world.y, (float) world.z);
        return surfaceRotation(surface, new Quaternionf()).transformInverse(v);
    }

    /** The yaw (in the surface's frame) that faces along {@code world}, or null if it points into/out of the surface. */
    public static @Nullable Float localYaw(Direction surface, Vec3 world) {
        Vector3f local = toLocal(surface, world);
        if (local.x * local.x + local.z * local.z < 1.0E-6F) return null;
        return (float) (Mth.atan2(local.z, local.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** World direction it faces with this yaw (in the surface's frame). */
    public static Vec3 forward(Direction surface, float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        Vector3f v = new Vector3f(-Mth.sin(rad), 0.0F, Mth.cos(rad));
        surfaceRotation(surface, new Quaternionf()).transform(v);
        return new Vec3(v.x, v.y, v.z);
    }

    // ---------------------------------------------------------------- tilt

    /** The other two axes, for a face on {@code axis}. */
    private static Direction.Axis[] tangents(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Y};
        };
    }

    private static Vec3 along(Direction.Axis axis, double value) {
        return switch (axis) {
            case X -> new Vec3(value, 0.0, 0.0);
            case Y -> new Vec3(0.0, value, 0.0);
            case Z -> new Vec3(0.0, 0.0, value);
        };
    }

    /**
     * The "up" of the ground under it (both sides: the server sizes the box with it, the client
     * draws with it). Measures how high the ground is (along its up) at 3 × 3 spots across its
     * footprint and fits a slope through them: flat ground gives its plain up, steps and slopes
     * lean it, and an edge it's walking over tips it toward the drop. Off the ground it's the plain up.
     */
    private Vec3 groundNormal(Direction surface) {
        Vec3 up = this.getUp();
        if (!this.getSettings().tilt() || (surface == Direction.DOWN && !this.mob.onGround())) return up;

        Direction.Axis a = surface.getAxis();
        Direction.Axis[] t = tangents(a);
        // Heights are measured along "up": sign flips the axis when up points toward negative
        double sign = -surface.getAxisDirection().getStep();
        AABB box = this.mob.getBoundingBox();
        double feet = sign > 0 ? box.min(a) : -box.max(a);
        double start = feet + (box.max(a) - box.min(a));
        double bottom = feet - TILT_DROP;

        Vec3 center = box.getCenter();
        double r = this.mob.getBbWidth() * TILT_SPREAD;
        double c1 = center.get(t[0]);
        double c2 = center.get(t[1]);

        // Everything solid in the columns it samples
        double lo = sign > 0 ? bottom : -start;
        double hi = sign > 0 ? start : -bottom;
        Vec3 min = along(a, lo).add(along(t[0], c1 - r - 0.01)).add(along(t[1], c2 - r - 0.01));
        Vec3 max = along(a, hi).add(along(t[0], c1 + r + 0.01)).add(along(t[1], c2 + r + 0.01));
        List<AABB> solids = new ArrayList<>();
        AABB area = new AABB(min.x, min.y, min.z, max.x, max.y, max.z);
        for (VoxelShape shape : this.mob.level().getBlockCollisions(this.mob, area)) {
            solids.addAll(shape.toAabbs());
        }
        if (solids.isEmpty()) return up;

        // Ground height in each column, from the top of its box down
        double[][] height = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double p1 = c1 + (i - 1) * r;
                double p2 = c2 + (j - 1) * r;
                double best = bottom;
                for (AABB s : solids) {
                    if (p1 < s.min(t[0]) || p1 > s.max(t[0]) || p2 < s.min(t[1]) || p2 > s.max(t[1])) continue;
                    double top = sign > 0 ? s.max(a) : -s.min(a);
                    double base = sign > 0 ? s.min(a) : -s.max(a);
                    if (base < start && top > start) {
                        // Solid all the way up past its box here: ground at least that high
                        best = start;
                        break;
                    }
                    if (top <= start + 1.0E-7 && top > best) best = top;
                }
                height[i][j] = best;
            }
        }

        double slope1 = 0.0, slope2 = 0.0;
        for (int k = 0; k < 3; k++) {
            slope1 += (height[2][k] - height[0][k]) / (2 * r);
            slope2 += (height[k][2] - height[k][0]) / (2 * r);
        }
        slope1 /= 3;
        slope2 /= 3;

        // Ground rising toward +t leans "up" back toward -t (the same on floors, walls and ceilings,
        // since heights are already measured along up)
        Vec3 n = up.add(along(t[0], -slope1)).add(along(t[1], -slope2));
        if (n.lengthSqr() < 1.0E-6) return up;
        n = n.normalize();

        // No more than MAX_TILT away from the face it's on
        double dot = n.dot(up);
        double cos = Math.cos(MAX_TILT);
        if (dot < cos) {
            Vec3 side = n.subtract(up.scale(dot));
            double length = side.length();
            if (length < 1.0E-6) return up;
            n = up.scale(cos).add(side.scale(Math.sin(MAX_TILT) / length));
        }
        return n;
    }

    // ---------------------------------------------------------------- client

    /**
     * Client, every tick: eases the model toward its surface, tilted to the ground under it, and
     * centers it in its box (the box is sized around the turned model, so centered it fits).
     */
    public void clientTick() {
        Direction s = this.getSurface();
        Quaternionf targetRot = modelRotation(s, this.getUp(), this.groundNormal(s), new Quaternionf());
        boolean first = !this.initialized;
        if (first) {
            this.initialized = true;
            this.rotation.set(targetRot);
        }
        this.prevRotation.set(this.rotation);
        this.prevOffset.set(this.offset);
        this.rotation.slerp(targetRot, TURN).normalize();

        // Model's middle (half its height above its feet, turned) on the middle of its box
        Vec3 e = this.getExtents();
        Vector3f middle = this.rotation.transform(new Vector3f(0.0F, this.mob.getBbHeight() / 2.0F, 0.0F));
        this.offset.set(0.0F, (float) (e.y / 2), 0.0F).sub(middle);
        if (first) this.prevOffset.set(this.offset);
    }

    /** Client: whether the model needs moving at all. */
    public boolean hasRenderRotation() {
        return this.initialized;
    }

    /** Client: the model's rotation and feet position this frame. */
    public void getRenderPose(float partialTick, Quaternionf rotDest, Vector3f offsetDest) {
        this.prevRotation.slerp(this.rotation, partialTick, rotDest);
        this.prevOffset.lerp(this.offset, partialTick, offsetDest);
    }
}
