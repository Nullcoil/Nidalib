package net.nullcoil.nidalib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.food.FoodData;
import net.nullcoil.nidalib.attribute.ModAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    // Target the two serverPlayer.isHurt() checks inside the FoodData tick loop
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z")
    )
    private boolean nidalib$stopHealingPastCorruption(ServerPlayer player, Operation<Boolean> original) {
        boolean isHurt = original.call(player); // see if vanilla thinks the player is hurt

        if (isHurt) {
            AttributeInstance corruptionAttr = player.getAttribute(ModAttributes.CORRUPTION);

            if (corruptionAttr != null && corruptionAttr.getValue() > 0) {
                float safeHealth = player.getMaxHealth() - (float) corruptionAttr.getValue();
                if (player.getHealth() >= safeHealth) return false;
            }
        }

        return isHurt;
    }
}