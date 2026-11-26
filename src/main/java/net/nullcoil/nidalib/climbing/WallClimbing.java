package net.nullcoil.nidalib.climbing;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumSet;

/**
 * Climbing state for one mob that can climb (see {@link WallClimbers}): which block face it's standing on (synced to
 * clients), how it picks a new one, and (client side) the smoothed model rotation.
 *
 * <p>The "surface" is the direction from the mob to the face it stands on: {@code DOWN} is the
 * floor (normal walking), {@code NORTH} a wall to its north, {@code UP} a ceiling.
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
    /** Share of the way the model turns toward a new surface each tick (client). */
    private static final float TURN = 0.35F;

    private final Mob mob;
    private final EntityDataAccessor<Direction> data;

    // Server
    private boolean touching = true;
    private int lostTicks;
    private int grabCooldown;
    private @Nullable Vec3 stuckAnchor;
    private int stuckTime;

    // Client
    private boolean initialized;
    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf prevRotation = new Quaternionf();
    private final Vector3f offset = new Vector3f();
    private final Vector3f prevOffset = new Vector3f();

    /** Made by Nidalib for each mob that can climb; get it with {@link #get}. */
    public WallClimbing(Mob mob, EntityDataAccessor<Direction> data) {
        this.mob = mob;
        this.data = data;
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

    // ---------------------------------------------------------------- state

    /** The face it's standing on ({@code DOWN} = floor). */
    public Direction getSurface() {
        // The box is first made inside Entity's constructor, before synced data exists
        SynchedEntityData entityData = this.mob.getEntityData();
        return entityData != null ? entityData.get(this.data) : Direction.DOWN;
    }

    /** Whether this is the synced value that holds its surface. */
    public boolean isSurfaceData(EntityDataAccessor<?> accessor) {
        return accessor == this.data;
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
        this.moveTo(Direction.DOWN, this.touching, true);
    }

    // ---------------------------------------------------------------- hitbox

    /**
     * Size of its box on this surface. Turning onto a wall turns the box with it: its height
     * becomes its depth out from the wall, and its width runs up the wall. Floors and ceilings
     * keep the normal box.
     */
    public Vec3 getExtents(Direction surface) {
        double w = this.mob.getBbWidth();
        double h = this.mob.getBbHeight();
        return switch (surface.getAxis()) {
            case Y -> new Vec3(w, h, w);
            case Z -> new Vec3(w, w, h);
            case X -> new Vec3(h, w, w);
        };
    }

    /** Its box at this position (bottom center, as always), turned for its current surface. */
    public AABB makeBoundingBox(Vec3 pos) {
        Vec3 e = this.getExtents(this.getSurface());
        return new AABB(pos.x - e.x / 2, pos.y, pos.z - e.z / 2, pos.x + e.x / 2, pos.y + e.y, pos.z + e.z / 2);
    }

    /**
     * Where it has to stand for its turned box to fit on {@code to}, or null if it doesn't fit.
     * The new box keeps the side against the new surface where it was (and the side against the
     * old one, if {@code keepFrom}), and stays centered on the other axes. Stepping from the floor
     * onto a wall, the box stands up in the corner, flush with both.
     */
    private @Nullable Vec3 placeFor(Direction from, Direction to, boolean keepFrom) {
        AABB old = this.mob.getBoundingBox();
        Vec3 size = this.getExtents(to);
        double[] min = new double[3];
        double[] max = new double[3];
        Direction.Axis[] axes = Direction.Axis.values();
        for (int i = 0; i < 3; i++) {
            Direction.Axis axis = axes[i];
            double extent = size.get(axis);
            Direction anchor = to.getAxis() == axis ? to
                    : keepFrom && from.getAxis() == axis ? from
                    : null;
            if (anchor == null) {
                double center = (old.min(axis) + old.max(axis)) / 2;
                min[i] = center - extent / 2;
                max[i] = center + extent / 2;
            } else if (anchor.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                max[i] = old.max(axis);
                min[i] = max[i] - extent;
            } else {
                min[i] = old.min(axis);
                max[i] = min[i] + extent;
            }
        }
        AABB box = new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
        if (!this.mob.level().noCollision(this.mob, box.deflate(1.0E-5))) return null;
        return new Vec3((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
    }

    /**
     * Switches to {@code to} and moves so its turned box fits. Returns false (and stays put) if
     * it doesn't fit, unless {@code force}: then it switches anyway, centered where it was
     * (letting go has to work even in a tight spot).
     */
    private boolean moveTo(Direction to, boolean keepFrom, boolean force) {
        Direction from = this.getSurface();
        if (from == to) return true;
        Vec3 pos = this.placeFor(from, to, keepFrom);
        if (pos == null && keepFrom) pos = this.placeFor(from, to, false);
        if (pos == null) {
            if (!force) return false;
            AABB old = this.mob.getBoundingBox();
            pos = new Vec3(old.getCenter().x, old.getCenter().y - this.getExtents(to).y / 2, old.getCenter().z);
        }
        this.setSurface(to);
        this.mob.setPos(pos);
        return true;
    }

    /**
     * Where its eyes are when it stands at {@code pos} (bottom center of its box): eye height out
     * from the face it's on, along its "up", instead of straight up. Used for everything that
     * asks for its eye position (looking, line of sight, suffocation).
     */
    public Vec3 getEyePosition(Vec3 pos) {
        Direction s = this.getSurface();
        double eye = this.mob.getEyeHeight();
        double w = this.mob.getBbWidth();
        double h = this.mob.getBbHeight();
        return switch (s) {
            case DOWN -> pos.add(0.0, eye, 0.0);
            case UP -> pos.add(0.0, h - eye, 0.0);
            // Its feet are on the wall side of its box (h/2 from the middle); eyes are eye height out from there
            default -> new Vec3(pos.x + s.getStepX() * (h / 2 - eye), pos.y + w / 2, pos.z + s.getStepZ() * (h / 2 - eye));
        };
    }

    /**
     * Vanilla's suffocation check, turned: a flat square 0.8 × its width across, at its eyes,
     * lying in the plane of the face it's on (vanilla's lies flat, which on a wall sticks out of
     * its turned box and into the wall it stands on).
     */
    public AABB getInWallCheckBox(Vec3 eye) {
        double f = this.mob.getBbWidth() * 0.8;
        double thin = 1.0E-6;
        return switch (this.getSurface().getAxis()) {
            case Y -> AABB.ofSize(eye, f, thin, f);
            case Z -> AABB.ofSize(eye, f, f, thin);
            case X -> AABB.ofSize(eye, thin, f, f);
        };
    }

    /** Loading: put it back on the face it was saved on, as it was (no turning, no moving). */
    public void restoreSurface(Direction surface) {
        if (surface != Direction.DOWN && !this.getSettings().canClimbOn(surface)) return;
        this.mob.getEntityData().set(this.data, surface);
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
        this.mob.getEntityData().set(this.data, surface);
        if (yaw != null) {
            this.mob.setYRot(yaw);
            this.mob.setYBodyRot(yaw);
            this.mob.setYHeadRot(yaw);
        }
        this.lostTicks = 0;
    }

    // ---------------------------------------------------------------- server

    /**
     * Server, every tick (the {@link WallClimberMoveControl} calls this): picks the surface.
     *
     * @param want where it wants to go, relative to its position, or null if it isn't moving
     */
    public void serverTick(@Nullable Vec3 want) {
        if (this.grabCooldown > 0) this.grabCooldown--;

        Direction current = this.getSurface();
        EnumSet<Direction> contacts = this.grabCooldown > 0 ? EnumSet.noneOf(Direction.class) : this.findContacts();
        Direction next = this.choose(current, contacts, want);

        if (next != current) {
            // Losing its grip has to work; stepping onto a face it doesn't fit against doesn't
            boolean letGo = next == Direction.DOWN && !contacts.contains(current);
            if (!this.moveTo(next, contacts.contains(current), letGo)) next = current;
        }
        this.touching = next == Direction.DOWN || contacts.contains(next);
        if (next != Direction.DOWN) this.mob.resetFallDistance();
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
     * meaningless on a wall. Re-aim it in the surface's frame instead.
     */
    public void alignHead() {
        Direction s = this.getSurface();
        if (s == Direction.DOWN) return;

        float body = this.mob.yBodyRot;
        float headYaw = body;
        float pitch = 0.0F;
        var look = this.mob.getLookControl();
        if (look.isLookingAtTarget()) {
            Vec3 eye = this.mob.getBoundingBox().getCenter();
            Vec3 dir = new Vec3(look.getWantedX() - eye.x, look.getWantedY() - eye.y, look.getWantedZ() - eye.z);
            Vector3f local = toLocal(s, dir);
            double horizontal = Math.sqrt(local.x * local.x + local.z * local.z);
            if (horizontal > 1.0E-4) {
                float yaw = (float) (Mth.atan2(local.z, local.x) * Mth.RAD_TO_DEG) - 90.0F;
                float max = this.mob.getMaxHeadYRot();
                headYaw = body + Mth.clamp(Mth.wrapDegrees(yaw - body), -max, max);
                pitch = Mth.clamp((float) (-Mth.atan2(local.y, horizontal) * Mth.RAD_TO_DEG), -this.mob.getMaxHeadXRot(), this.mob.getMaxHeadXRot());
            }
        }
        this.mob.setYHeadRot(headYaw);
        this.mob.setXRot(pitch);
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

    /** Where the model's feet go, relative to the entity's position: the middle of its (turned) box's face on that side. */
    private Vector3f contactOffset(Direction surface, Vector3f dest) {
        float w = this.mob.getBbWidth();
        float h = this.mob.getBbHeight();
        return switch (surface) {
            case DOWN -> dest.set(0.0F, 0.0F, 0.0F);
            case UP -> dest.set(0.0F, h, 0.0F);
            // On a wall the box is w tall and h deep; its wall side is h/2 from its middle
            default -> dest.set(surface.getStepX() * h / 2.0F, w / 2.0F, surface.getStepZ() * h / 2.0F);
        };
    }

    // ---------------------------------------------------------------- client

    /** Client, every tick: eases the model toward its surface. */
    public void clientTick() {
        Direction s = this.getSurface();
        Quaternionf targetRot = surfaceRotation(s, new Quaternionf());
        Vector3f targetOffset = this.contactOffset(s, new Vector3f());
        if (!this.initialized) {
            this.initialized = true;
            this.rotation.set(targetRot);
            this.offset.set(targetOffset);
        }
        this.prevRotation.set(this.rotation);
        this.prevOffset.set(this.offset);
        this.rotation.slerp(targetRot, TURN).normalize();
        this.offset.lerp(targetOffset, TURN);
    }

    /** Client: whether the model needs turning at all (on a surface, or still turning back to the floor). */
    public boolean hasRenderRotation() {
        return this.initialized && (this.isClimbing() || Math.abs(this.rotation.w) < 0.9999F || Math.abs(this.prevRotation.w) < 0.9999F);
    }

    /** Client: the model's rotation and feet position this frame. */
    public void getRenderPose(float partialTick, Quaternionf rotDest, Vector3f offsetDest) {
        this.prevRotation.slerp(this.rotation, partialTick, rotDest);
        this.prevOffset.lerp(this.offset, partialTick, offsetDest);
    }
}
