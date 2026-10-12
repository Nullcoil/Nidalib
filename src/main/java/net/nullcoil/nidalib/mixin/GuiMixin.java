package net.nullcoil.nidalib.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.nullcoil.nidalib.Nidalib;
import net.nullcoil.nidalib.attribute.ModAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class GuiMixin {

    @Shadow
    protected abstract LivingEntity getPlayerVehicleWithHealth();
    @Shadow protected abstract int getVehicleMaxHearts(LivingEntity livingEntity);

    @Unique
    private static final Identifier CORRUPTED_FULL = Nidalib.id("hud/heart/corrupted_full");
    @Unique private static final Identifier CORRUPTED_HALF = Nidalib.id("hud/heart/corrupted_half");
    @Unique private static final Identifier CORRUPTED_HARDCORE_FULL = Nidalib.id("hud/heart/corrupted_hardcore_full");
    @Unique private static final Identifier CORRUPTED_HARDCORE_HALF = Nidalib.id("hud/heart/corrupted_hardcore_half");
    @Unique private static final Identifier CORRUPTED_VEHICLE_HALF = Nidalib.id("hud/heart/corrupted_vehicle_half");

    @Unique private float nidalib$playerMaxHealth = 0;
    @Unique private float nidalib$playerCorruption = 0;
    @Unique private int nidalib$playerHeartIndex = -1;

    @Inject(method = "extractHearts", at = @At("HEAD"))
    private void nidalib$startPlayerRender(GuiGraphicsExtractor graphics, Player player, int xLeft, int yLineBase, int healthRowHeight, int heartOffsetIndex, float maxHealth, int currentHealth, int oldHealth, int absorption, boolean blink, CallbackInfo ci) {
        nidalib$playerMaxHealth = player.getMaxHealth();
        var attr = player.getAttribute(ModAttributes.CORRUPTION);
        nidalib$playerCorruption = attr != null ? (float) attr.getValue() : 0f;

        // Vanilla loops backward from (Max Health + Absorption) down to 0.
        int p = Mth.ceil((double)maxHealth / 2.0D);
        int q = Mth.ceil((double)absorption / 2.0D);
        nidalib$playerHeartIndex = p + q - 1;
    }

    @Inject(method = "extractHeart", at = @At("TAIL"))
    private void nidalib$overlayPlayerCrackedHeart(
            GuiGraphicsExtractor graphics, Hud.HeartType type, int xo, int yo, boolean isHardcore, boolean blinks, boolean half, CallbackInfo ci
    ) {
        if (type.toString().equals("CONTAINER")) {
            int currentS = nidalib$playerHeartIndex;
            nidalib$playerHeartIndex--;

            if (nidalib$playerCorruption > 0) {
                float safeHealth = nidalib$playerMaxHealth - nidalib$playerCorruption;
                int leftHalf = currentS * 2;
                int rightHalf = currentS * 2 + 1;

                if (leftHalf < nidalib$playerMaxHealth) {
                    boolean fullyCorrupted = leftHalf >= safeHealth && rightHalf >= safeHealth;
                    boolean halfCorrupted = leftHalf < safeHealth && rightHalf >= safeHealth;

                    if (fullyCorrupted) {
                        Identifier sprite = isHardcore ? CORRUPTED_HARDCORE_FULL : CORRUPTED_FULL;
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, xo, yo, 9, 9);
                    } else if (halfCorrupted) {
                        Identifier sprite = isHardcore ? CORRUPTED_HARDCORE_HALF : CORRUPTED_HALF;
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, xo, yo, 9, 9);
                    }
                }
            }
        }
    }

    @Inject(method = "extractVehicleHealth", at = @At("TAIL"))
    private void nidalib$overlayVehicleCrackedHearts(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        LivingEntity vehicle = this.getPlayerVehicleWithHealth();
        if (vehicle == null) return;

        var attr = vehicle.getAttribute(ModAttributes.CORRUPTION);
        float corruption = attr != null ? (float) attr.getValue() : 0f;

        if (corruption <= 0) return;

        float maxHealth = vehicle.getMaxHealth();
        int hearts = this.getVehicleMaxHearts(vehicle);
        if (hearts == 0) return;

        float safeHealth = maxHealth - corruption;

        int y = graphics.guiHeight() - 39;
        int rightEdgeX = graphics.guiWidth() / 2 + 91;
        int currentHeartIndex = 0;

        for (int n = 0; hearts > 0; n += 20) {
            int rowHearts = Math.min(hearts, 10);
            hearts -= rowHearts;

            for (int p = 0; p < rowHearts; ++p) {
                int x = rightEdgeX - p * 8 - 9;

                int leftHalf = currentHeartIndex * 2;
                int rightHalf = currentHeartIndex * 2 + 1;

                if (leftHalf < maxHealth) {
                    boolean fullyCorrupted = leftHalf >= safeHealth && rightHalf >= safeHealth;
                    boolean halfCorrupted = leftHalf < safeHealth && rightHalf >= safeHealth;

                    if (fullyCorrupted) {
                        // Feel free to swap this if you end up making a corrupted_vehicle_full!
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CORRUPTED_FULL, x, y, 9, 9);
                    } else if (halfCorrupted) {
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CORRUPTED_VEHICLE_HALF, x, y, 9, 9);
                    }
                }
                currentHeartIndex++;
            }
            y -= 10;
        }
    }
}
