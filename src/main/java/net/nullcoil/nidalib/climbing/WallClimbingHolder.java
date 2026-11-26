package net.nullcoil.nidalib.climbing;

import org.jetbrains.annotations.Nullable;

/** Added to every Mob by mixin. Use {@link WallClimbing#get} instead of calling this. */
public interface WallClimbingHolder {
    /** This mob's climbing state, or null if it can't climb. */
    @Nullable
    WallClimbing nidalib$getWallClimbing();
}
