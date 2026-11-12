package net.nullcoil.nidalib.mixin;

import net.minecraft.world.entity.Entity;
import net.nullcoil.nidalib.lava.LavaFire;
import net.nullcoil.nidalib.lava.LavaHeat;
import net.nullcoil.nidalib.lava.LavaStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags fire caught from lava with the lava's stage.
 *
 * <p>lavaIgnite records "lava touched me this tick, at this stage". Any time the entity's
 * fire is topped up, if that happened within the last tick the fire came from lava and gets
 * the stage; otherwise it's some other fire and the stage is cleared.
 *
 * <p>Server only. The stage is a synced attachment, and nothing on the client writes it,
 * so the client's own lava prediction can't overwrite what the server decided.
 */
@Mixin(Entity.class)
public abstract class LavaFireMixin {
    @Shadow
    public abstract int getRemainingFireTicks();

    @Unique
    private long nidalib$lavaTick = Long.MIN_VALUE;
    @Unique
    private LavaStage nidalib$lavaStage = LavaStage.VANILLA;

    @Inject(method = "lavaIgnite", at = @At("HEAD"))
    private void nidalib$markLava(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        nidalib$lavaTick = self.level().getGameTime();
        nidalib$lavaStage = LavaFire.stageFor(LavaHeat.faded(self.level(), self.blockPosition()));
    }

    @Inject(method = "setRemainingFireTicks", at = @At("HEAD"))
    private void nidalib$tagFire(int remainingTicks, CallbackInfo ci) {
        // Only when catching or topping up fire, not while it burns down
        if (remainingTicks <= 0 || remainingTicks < getRemainingFireTicks()) return;

        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        long sinceLava = self.level().getGameTime() - nidalib$lavaTick;
        boolean fromLava = sinceLava >= 0 && sinceLava <= 1;
        LavaFire.set(self, fromLava ? nidalib$lavaStage : null);
    }
}
