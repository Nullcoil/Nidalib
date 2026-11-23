package net.nullcoil.nidalib.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * An effect whose duration is a <b>build-up</b>, not a timer: the counterpart to {@link CountdownEffect}.
 *
 * <p>The duration is how far the build-up has gotten, from 0 to {@link #threshold()}. Sources push it
 * up with {@link #buildUp}; like any effect it drains by 1 a tick, so it only climbs while a source
 * adds more than that, and it wears off at 0 once nothing feeds it.
 *
 * <p>Subclasses ramp their effect with {@link #progress} (0 to 1) in {@link #onBuildupTick}, and
 * {@link #onPeak} fires once it reaches the threshold, after which the effect is removed.
 */
public abstract class BuildupEffect extends MobEffect {
    private final int threshold;
    private Holder<MobEffect> holder;

    protected BuildupEffect(MobEffectCategory category, int color, int threshold) {
        super(category, color);
        if (threshold <= 0) throw new IllegalArgumentException("threshold must be positive: " + threshold);
        this.threshold = threshold;
    }

    /** Build-up at which the effect peaks. */
    public final int threshold() {
        return threshold;
    }

    /** How far a build-up has gotten: 0 (none) to 1 (peaked). */
    public final float progress(int buildUp) {
        return Math.min(1.0F, Math.max(0.0F, buildUp / (float) threshold));
    }

    /** How far this entity's build-up has gotten, or 0 without the effect. */
    public final float progress(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(holder());
        return instance != null ? progress(instance.getDuration()) : 0.0F;
    }

    /** Adds {@code amount} to the build-up, capped at the threshold. Returns the new build-up. */
    public final int buildUp(LivingEntity target, int amount) {
        return buildUp(target, amount, 0);
    }

    /**
     * Adds {@code amount} (positive) to the build-up, capped at the threshold. Keeps the higher of
     * the current and given amplifier. Server side only. Returns the new build-up.
     */
    public final int buildUp(LivingEntity target, int amount, int amplifier) {
        MobEffectInstance current = target.getEffect(holder());
        int before = current != null ? current.getDuration() : 0;
        if (amount <= 0 || target.level().isClientSide() || !target.isAlive() || target.isRemoved()) return before;

        int amp = current != null ? Math.max(current.getAmplifier(), amplifier) : amplifier;
        int next = Math.min(before + amount, threshold);
        target.addEffect(createInstance(next, amp));
        onBuiltUp(target, progress(next), amp);
        return next;
    }

    /** The instance {@link #buildUp} applies. By default: no particles, but shows its icon. */
    protected MobEffectInstance createInstance(int buildUp, int amplifier) {
        return new MobEffectInstance(holder(), buildUp, amplifier, false, false, true);
    }

    /** This effect's registry holder (resolved on first use, so only after registration). */
    protected final Holder<MobEffect> holder() {
        if (holder == null) holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this);
        return holder;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public final boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        MobEffectInstance instance = entity.getEffect(holder());
        if (instance == null) return true;

        float progress = progress(instance.getDuration());
        onBuildupTick(level, entity, amplifier, progress);

        if (progress >= 1.0F && canPeak(entity)) {
            onPeak(level, entity, amplifier);
            return false; // removes the effect, which calls onReset
        }
        return true;
    }

    @Override
    public void onEffectRemoved(MobEffectInstance instance, LivingEntity entity) {
        super.onEffectRemoved(instance, entity);
        onReset(entity);
    }

    // ---- Hooks ----

    /** Every tick while the effect is active, rising or draining. Ramp the effect here. */
    protected void onBuildupTick(ServerLevel level, LivingEntity entity, int amplifier, float progress) {}

    /** A source just added to the build-up (only while it's being fed, not while it drains). */
    protected void onBuiltUp(LivingEntity entity, float progress, int amplifier) {}

    /** The build-up reached the threshold. The effect is removed right after. */
    protected abstract void onPeak(ServerLevel level, LivingEntity entity, int amplifier);

    /** Whether the build-up can peak on this entity. By default, not on creative or spectator players. */
    protected boolean canPeak(LivingEntity entity) {
        return !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /** The effect ended, by draining out, peaking, milk, a totem or a command. Undo the ramp here. */
    protected void onReset(LivingEntity entity) {}
}