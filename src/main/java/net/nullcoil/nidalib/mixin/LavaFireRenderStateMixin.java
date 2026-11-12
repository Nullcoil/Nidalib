package net.nullcoil.nidalib.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.nullcoil.nidalib.client.lava.LavaFireRenderState;
import net.nullcoil.nidalib.lava.LavaStage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class LavaFireRenderStateMixin implements LavaFireRenderState {
    @Unique
    private @Nullable LavaStage nidalib$lavaFireStage;

    @Override
    public @Nullable LavaStage nidalib$getLavaFireStage() {
        return nidalib$lavaFireStage;
    }

    @Override
    public void nidalib$setLavaFireStage(@Nullable LavaStage stage) {
        nidalib$lavaFireStage = stage;
    }
}
