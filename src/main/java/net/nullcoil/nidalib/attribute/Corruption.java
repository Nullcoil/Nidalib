package net.nullcoil.nidalib.attribute;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.nullcoil.nidalib.Nidalib;

/**
 * The one place Corruption is changed and enforced. Corruption eats into max health:
 * health can't go above {@code maxHealth - corruption}, and reaching max health kills.
 *
 * <p>Always change Corruption through {@link #add}, {@link #remove} or {@link #set} rather
 * than the attribute directly, so the health clamp and the death check always happen.
 * All of them are server-side and do nothing on the client.
 */
public final class Corruption {
    private Corruption() {}

    /** Death by Corruption. Bypasses armor, effects, enchantments and totems (see its tags). */
    public static final ResourceKey<DamageType> DEATH =
            ResourceKey.create(Registries.DAMAGE_TYPE, Nidalib.id("corruption"));

    /** Called by Nidalib's initializer. */
    public static void init() {
        // Max health can drop with no Corruption change (Health Boost ending, modifiers removed),
        // which can leave a player at or over the limit; catch that once per tick.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Player player : server.getPlayerList().getPlayers()) {
                check(player);
            }
        });
    }

    /** Current Corruption, including modifiers. 0 for entities without the attribute. */
    public static double get(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(ModAttributes.CORRUPTION);
        return attr == null ? 0 : attr.getValue();
    }

    /** Adds Corruption, then clamps health or kills. */
    public static void add(LivingEntity entity, double amount) {
        AttributeInstance attr = attribute(entity);
        if (attr == null || amount <= 0) return;
        attr.setBaseValue(attr.getBaseValue() + amount);
        check(entity);
    }

    /** Removes Corruption (never below 0). */
    public static void remove(LivingEntity entity, double amount) {
        AttributeInstance attr = attribute(entity);
        if (attr == null || amount <= 0) return;
        attr.setBaseValue(Math.max(0, attr.getBaseValue() - amount));
    }

    /** Sets the base Corruption, then clamps health or kills. */
    public static void set(LivingEntity entity, double value) {
        AttributeInstance attr = attribute(entity);
        if (attr == null) return;
        attr.setBaseValue(Math.max(0, value));
        check(entity);
    }

    /**
     * Enforces the limit: kills if Corruption has reached max health, otherwise pulls health
     * down to {@code maxHealth - corruption} (the setHealth mixin does the clamping).
     * Creative and spectator players are left alone.
     */
    public static void check(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) return;

        double corruption = get(entity);
        if (corruption <= 0) return;

        if (corruption >= entity.getMaxHealth()) {
            entity.hurtServer(level, deathSource(entity), Float.MAX_VALUE);
        } else if (entity.getHealth() > entity.getMaxHealth() - corruption) {
            entity.setHealth(entity.getHealth());
        }
    }

    /** Credits the last attacker, so the death message reads "...fighting X" when there is one. */
    private static DamageSource deathSource(LivingEntity entity) {
        LivingEntity attacker = entity.getLastHurtByMob();
        return attacker != null
                ? entity.damageSources().source(DEATH, attacker)
                : entity.damageSources().source(DEATH);
    }

    private static AttributeInstance attribute(LivingEntity entity) {
        if (entity.level().isClientSide()) return null;
        return entity.getAttribute(ModAttributes.CORRUPTION);
    }
}
