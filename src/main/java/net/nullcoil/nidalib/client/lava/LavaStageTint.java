package net.nullcoil.nidalib.client.lava;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.nullcoil.nidalib.lava.LavaHeat;
import net.nullcoil.nidalib.lava.LavaStage;
import org.jspecify.annotations.Nullable;

/**
 * Item tint that shows one lava heat stage's layer by its opacity, following the heat where
 * the local player is standing. The lava bucket stacks one overlay per stage; for any heat,
 * the cooler stage is fully opaque, the hotter one fades in on top by the blend, and every
 * other stage is fully transparent, so it crossfades like lava in the world.
 *
 * <pre>{@code { "type": "nidalib:lava_stage", "stage": "soul" } }</pre>
 */
@Environment(EnvType.CLIENT)
public record LavaStageTint(LavaStage stage) implements ItemTintSource {
    private static final Codec<LavaStage> STAGE_CODEC = Codec.STRING.xmap(LavaStageTint::stageById, s -> s.id);

    public static final MapCodec<LavaStageTint> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            STAGE_CODEC.fieldOf("stage").forGetter(LavaStageTint::stage)
    ).apply(instance, LavaStageTint::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        LocalPlayer player = Minecraft.getInstance().player;
        ClientLevel clientLevel = Minecraft.getInstance().level;
        float heat = player == null || clientLevel == null
                ? LavaHeat.DEFAULT_HEAT
                : LavaHeat.faded(clientLevel, player.blockPosition());

        int lower = Math.min((int) Math.floor(heat), LavaStage.MAX);
        int alpha;
        if (stage.ordinal() == lower) alpha = 255;
        else if (stage.ordinal() == lower + 1) alpha = Math.round((heat - lower) * 255f);
        else alpha = 0;
        return (alpha << 24) | 0x00FFFFFF;
    }

    @Override
    public MapCodec<LavaStageTint> type() {
        return MAP_CODEC;
    }

    private static LavaStage stageById(String id) {
        for (LavaStage stage : LavaStage.values()) {
            if (stage.id.equals(id)) return stage;
        }
        throw new IllegalArgumentException("Unknown lava stage: " + id);
    }
}
