package net.nullcoil.nidalib;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.world.level.block.Blocks;
import net.nullcoil.nidalib.client.lava.LavaCauldronModel;
import net.nullcoil.nidalib.client.lava.LavaHeatClient;

public class NidalibClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LavaHeatClient.init();

		// Lava cauldrons draw their lava in the heat stage of their biome
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE,
				(model, ctx) -> ctx.state().is(Blocks.LAVA_CAULDRON) ? new LavaCauldronModel(model) : model));
	}
}
