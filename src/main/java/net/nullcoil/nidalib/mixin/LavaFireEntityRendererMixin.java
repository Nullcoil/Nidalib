package net.nullcoil.nidalib.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.nullcoil.nidalib.client.lava.LavaFireRenderState;
import net.nullcoil.nidalib.lava.LavaFire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class LavaFireEntityRendererMixin {
    /** Copies the entity's lava fire stage into its render state, for the flame renderer. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void nidalib$extractLavaFire(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        ((LavaFireRenderState) state).nidalib$setLavaFireStage(LavaFire.get(entity));
    }
}
