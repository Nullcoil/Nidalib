package net.nullcoil.nidalib.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * An effect that does something once, when its timer runs out on its own. Removing it any
 * other way (milk, a Totem of Undying, {@code /effect clear}, dying) does nothing.
 *
 * <p>When the countdown ends, the part for whoever it was on runs first
 * ({@link #onPlayerCountdownEnd} or {@link #onMobCountdownEnd}), then the part for everyone
 * ({@link #onCountdownEnd}).
 */
public abstract class CountdownEffect extends MobEffect {
    /**
     * Entities whose countdown is ending right now. What happens at the end can clear this
     * same entity's effects (a Totem of Undying popped by a Volatile blast clears them all),
     * which calls onEffectRemoved again from inside it; this stops it going off twice.
     */
    private static final Set<LivingEntity> ENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    protected CountdownEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public final void onEffectRemoved(MobEffectInstance instance, LivingEntity entity) {
        super.onEffectRemoved(instance, entity);
        // Only when the timer ran out: anything else removes it with time still left
        if (instance.getDuration() > 0 || instance.isInfiniteDuration()) return;
        if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) return;
        if (!ENDING.add(entity)) return; // already ending: this is a removal from inside it

        int amplifier = instance.getAmplifier();
        try {
            if (entity instanceof ServerPlayer player) {
                this.onPlayerCountdownEnd(level, player, amplifier);
            } else {
                this.onMobCountdownEnd(level, entity, amplifier);
            }
            this.onCountdownEnd(level, entity, amplifier);
        } finally {
            ENDING.remove(entity);
        }
    }

    /** When the countdown ends on a player. Runs before {@link #onCountdownEnd}. */
    protected void onPlayerCountdownEnd(ServerLevel level, ServerPlayer player, int amplifier) {}

    /** When the countdown ends on anything that isn't a player. Runs before {@link #onCountdownEnd}. */
    protected void onMobCountdownEnd(ServerLevel level, LivingEntity entity, int amplifier) {}

    /** When the countdown ends, on anything. */
    protected void onCountdownEnd(ServerLevel level, LivingEntity entity, int amplifier) {}
}
