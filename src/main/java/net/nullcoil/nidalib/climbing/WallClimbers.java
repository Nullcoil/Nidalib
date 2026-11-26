package net.nullcoil.nidalib.climbing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns wall climbing on for any mob type, vanilla or modded, without touching its class.
 *
 * <pre>{@code
 * // In your ModInitializer (common, so client and server agree):
 * WallClimbers.register(EntityType.SPIDER, ClimbSettings.WALLS);
 * WallClimbers.register(EntityType.CAVE_SPIDER, ClimbSettings.WALLS);
 * WallClimbers.register(EntityType.SILVERFISH, ClimbSettings.builder().speedFactor(1.0).build());
 * }</pre>
 *
 * <p>A registered mob gets {@link WallClimberNavigation} and {@link WallClimberMoveControl}
 * swapped in when it's created. If its class sets its own move control in its constructor,
 * that one wins: make it extend {@code WallClimberMoveControl} instead.
 *
 * <p>Register during mod initialization, before any of those mobs exist. Subtypes have their
 * own entity type (cave spiders aren't spiders here), so register each one.
 */
public final class WallClimbers {
    private static final Map<EntityType<?>, ClimbSettings> REGISTRY = new ConcurrentHashMap<>();

    private WallClimbers() {
    }

    /** Lets every mob of this type climb. Registering a type again replaces its settings. */
    public static void register(EntityType<? extends Mob> type, ClimbSettings settings) {
        REGISTRY.put(type, settings);
    }

    /**
     * The settings this entity climbs with, or null if it can't climb. A {@link WallClimber}
     * gives its own (so they can change at runtime); anything else uses its type's registration.
     */
    public static @Nullable ClimbSettings get(Entity entity) {
        if (!(entity instanceof Mob)) return null;
        if (entity instanceof WallClimber climber) return climber.getClimbSettings();
        return REGISTRY.get(entity.getType());
    }

    /** Whether this entity climbs at all. Doesn't ask a {@link WallClimber} for its settings, so it's safe mid-construction. */
    public static boolean canClimb(Entity entity) {
        return entity instanceof Mob && (entity instanceof WallClimber || REGISTRY.containsKey(entity.getType()));
    }
}
