package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.core.BlockPos;
import net.nullcoil.nidalib.client.lava.LavaFogColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Under-lava fog takes the color of the lava you're in (heat stage and border fade). */
@Mixin(LavaFogEnvironment.class)
public abstract class LavaFogColorMixin {
    @ModifyReturnValue(method = "getBaseColor", at = @At("RETURN"))
    private int nidalib$heatColor(int original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return original;
        BlockPos pos = BlockPos.containing(minecraft.gameRenderer.mainCamera().position());
        return LavaFogColor.at(minecraft.level, pos, original);
    }
}
