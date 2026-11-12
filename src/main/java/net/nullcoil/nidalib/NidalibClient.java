package net.nullcoil.nidalib;

import net.fabricmc.api.ClientModInitializer;
import net.nullcoil.nidalib.client.lava.LavaHeatClient;

public class NidalibClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LavaHeatClient.init();
	}
}
