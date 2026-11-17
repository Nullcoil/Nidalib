package net.nullcoil.nidalib.mixin.c2me;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.nullcoil.nidalib.worldgen.biome.SubBiomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C2ME's GPU world generation fills chunk biomes without calling the biome source, so
 * sub-biomes never reach the chunk: /locate (which asks the biome source) finds them,
 * but the stored biomes, F3 and everything built on them (surface rules, carvers,
 * features, mob spawns) still say the parent biome.
 *
 * <p>This applies sub-biomes to the stored biomes right before the surface step, the
 * first step after biomes and noise that reads them. Only applied when C2ME is loaded
 * (see {@code C2MEMixinPlugin}); without it, the biome source hook already covers this.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class C2MESubBiomesMixin {
    @Inject(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
            at = @At("HEAD"))
    private void nidalib$applySubBiomes(CallbackInfo ci, @Local(argsOnly = true) RandomState randomState,
                                        @Local(argsOnly = true) ChunkAccess chunk) {
        ChunkGenerator generator = (ChunkGenerator) (Object) this;
        SubBiomes.applyToChunk(chunk, generator.getBiomeSource(), randomState.sampler());
    }
}
