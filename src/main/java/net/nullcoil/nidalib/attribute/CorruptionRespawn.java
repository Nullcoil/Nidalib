package net.nullcoil.nidalib.attribute;

import net.minecraft.server.level.ServerPlayer;

/**
 * What happens to a player who respawns after dying of Corruption. Swap it out with
 * {@link Corruption#setRespawnHandler}, e.g. {@code Corruption.setRespawnHandler((oldPlayer, newPlayer) -> {});}
 * to let them respawn clean.
 *
 * <p>Only runs for deaths caused by Corruption itself (not other deaths while corrupted), on
 * the server, while the new player is being set up. The new player starts with no Corruption
 * and full health; a handler that does nothing means a clean respawn.
 */
@FunctionalInterface
public interface CorruptionRespawn {
    /**
     * @param oldPlayer the body that died (still has its old Corruption and attributes)
     * @param newPlayer the respawned player
     */
    void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer);

    /**
     * The default: respawn with Corruption at max health − 1, which leaves exactly 1 health
     * (the health clamp brings it down).
     */
    CorruptionRespawn DEFAULT = (oldPlayer, newPlayer) ->
            Corruption.set(newPlayer, newPlayer.getMaxHealth() - 1.0);
}
