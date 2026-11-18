package net.nullcoil.nidalib.mixin.voxy;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.nidalib.client.lava.LavaVoxy;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Voxy bakes each fluid into a texture once, with the vanilla fluid renderer. While it
 * does, lava is drawn with its gray copy, which Voxy then tints per biome (see LavaVoxy).
 */
@Mixin(targets = "me.cortex.voxy.client.core.model.bakery.SoftwareModelTextureBakery")
public abstract class VoxyFluidBakeMixin {
    @WrapMethod(method = "bakeFluidState")
    private void nidalib$grayLava(BlockState state, int face, Operation<Void> original) {
        LavaVoxy.beginBake();
        try {
            original.call(state, face);
        } finally {
            LavaVoxy.endBake();
        }
    }
}
