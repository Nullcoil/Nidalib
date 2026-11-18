package net.nullcoil.nidalib.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.DripParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import net.nullcoil.nidalib.client.lava.LavaParticles;
import net.nullcoil.nidalib.lava.LavaStage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lava drips (hanging, falling and landing) in their lava stage's colors. The textures are
 * white and tinted, so the tint is run through the stage.
 *
 * <p>Tints arrive two ways: {@code setColor} right after spawning (vanilla's providers, and
 * Subtle Effects' droplets, which are drips too), and the cooling hanging drip writing its
 * fading color straight into the fields every tick. The override handles the first; the tick
 * hook catches the second by noticing the color isn't the one it last wrote, so a color is
 * never recolored twice.
 */
@Mixin(DripParticle.class)
public abstract class LavaDripParticleMixin extends SingleQuadParticle {
    @Shadow @Final private Fluid type;

    @Unique private LavaStage nidalib$stage;
    @Unique private float nidalib$lastR = Float.NaN, nidalib$lastG = Float.NaN, nidalib$lastB = Float.NaN;

    protected LavaDripParticleMixin(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
    }

    /** This drip's stage, or null if it isn't lava or is plain vanilla lava. */
    @Unique
    private LavaStage nidalib$stage() {
        if (nidalib$stage == null) {
            nidalib$stage = this.type != null && this.type.is(FluidTags.LAVA)
                    ? LavaParticles.stageAt(this.level, this.x, this.y, this.z, this.random)
                    : LavaStage.VANILLA;
        }
        return nidalib$stage.isGenerated() ? nidalib$stage : null;
    }

    @Override
    public void setColor(float r, float g, float b) {
        LavaStage stage = this.nidalib$stage();
        if (stage != null) {
            float[] rgb = LavaParticles.recolor(r, g, b, stage);
            r = rgb[0];
            g = rgb[1];
            b = rgb[2];
            nidalib$lastR = r;
            nidalib$lastG = g;
            nidalib$lastB = b;
        }
        super.setColor(r, g, b);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void nidalib$recolorTick(CallbackInfo ci) {
        if (this.rCol == nidalib$lastR && this.gCol == nidalib$lastG && this.bCol == nidalib$lastB) return;
        LavaStage stage = this.nidalib$stage();
        if (stage == null) return;
        this.setColor(this.rCol, this.gCol, this.bCol);
    }
}
