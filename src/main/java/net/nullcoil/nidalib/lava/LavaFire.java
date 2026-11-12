package net.nullcoil.nidalib.lava;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.nullcoil.nidalib.Nidalib;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers which {@link LavaStage} an entity's fire came from, so the flames can be
 * drawn in that stage's colors. Only fire caught from lava gets a stage; any other fire
 * (netherrack, fire blocks, flaming arrows...) clears it and burns in default colors.
 *
 * <p>The soul stage is deliberately left alone: soul-stage lava fire is handled by a real
 * soul fire system (e.g. Prometheus via Soulscorch), and recoloring on top would fight it.
 * Only lava at full soul heat counts as soul; anything short of it, even the first block
 * past a soul biome's edge, burns in the nearest recolorable stage instead.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LavaFire {
    private LavaFire() {}

    /** Stage ordinal, synced to every client tracking the entity. Absent = default fire. */
    private static final AttachmentType<Integer> STAGE = AttachmentRegistry.create(
            Nidalib.id("lava_fire_stage"),
            builder -> builder
                    .persistent(Codec.INT)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /** Called by Nidalib's initializer to load the attachment type. */
    public static void init() {}

    /**
     * The fire stage for lava at this heat: soul only at 100% soul heat, otherwise the
     * nearest stage below soul. So the switch to real soul fire happens exactly where
     * the lava is fully soul-colored.
     */
    public static LavaStage stageFor(float heat) {
        if (heat >= LavaStage.SOUL.ordinal()) return LavaStage.SOUL;
        return LavaStage.nearest(Math.min(heat, LavaStage.SOUL.ordinal() - 1));
    }

    /** Whether lava at this heat sets things on soul fire (see {@link #stageFor}). */
    public static boolean isSoul(float heat) {
        return stageFor(heat) == LavaStage.SOUL;
    }

    /** Whether fire from this stage gets recolored by Nidalib. */
    public static boolean recolors(LavaStage stage) {
        return stage.isGenerated() && stage != LavaStage.SOUL;
    }

    /** The stage this entity's fire came from, or null for default-colored fire. */
    @Nullable
    public static LavaStage get(Entity entity) {
        Integer ordinal = entity.getAttached(STAGE);
        if (ordinal == null || ordinal < 0 || ordinal > LavaStage.MAX) return null;
        LavaStage stage = LavaStage.values()[ordinal];
        return recolors(stage) ? stage : null;
    }

    public static void set(Entity entity, @Nullable LavaStage stage) {
        if (stage == null || !recolors(stage)) {
            if (entity.hasAttached(STAGE)) entity.removeAttached(STAGE);
        } else if (!Integer.valueOf(stage.ordinal()).equals(entity.getAttached(STAGE))) {
            entity.setAttached(STAGE, stage.ordinal());
        }
    }
}
