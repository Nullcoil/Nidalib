package net.nullcoil.nidalib.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.nullcoil.nidalib.attribute.ModAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    public abstract float getMaxHealth();
    @Shadow public abstract AttributeInstance getAttribute(Holder<Attribute> attribute);

    @Inject(method = "createLivingAttributes", at = @At("RETURN"))
    private static void soulscorch$addAllMobCorruption(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.getReturnValue().add(ModAttributes.CORRUPTION);
    }

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float soulscorch$clampHealthToCorruption(float hp) {
        AttributeInstance corruptionAttr = this.getAttribute(ModAttributes.CORRUPTION);
        if (corruptionAttr != null) {
            float corruption = (float) corruptionAttr.getValue();
            float maxAllowedHealth = this.getMaxHealth() - corruption;

            return Math.min(hp, Math.max(0, maxAllowedHealth));
        }
        return hp;
    }
}
