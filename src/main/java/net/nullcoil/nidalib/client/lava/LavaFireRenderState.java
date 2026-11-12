package net.nullcoil.nidalib.client.lava;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.nullcoil.nidalib.lava.LavaStage;
import org.jetbrains.annotations.Nullable;

/** Added to EntityRenderState by mixin: the lava stage of the entity's fire, if any. */
@Environment(EnvType.CLIENT)
public interface LavaFireRenderState {
    @Nullable
    LavaStage nidalib$getLavaFireStage();

    void nidalib$setLavaFireStage(@Nullable LavaStage stage);
}
