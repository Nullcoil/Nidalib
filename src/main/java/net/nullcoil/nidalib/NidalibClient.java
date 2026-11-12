package net.nullcoil.nidalib;

import net.fabricmc.api.ClientModInitializer;
import net.nullcoil.nidalib.client.lava.LavaHeat;

public class NidalibClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LavaHeat.init();
	}
}
