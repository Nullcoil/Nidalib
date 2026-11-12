package net.nullcoil.nidalib;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;
import net.nullcoil.nidalib.lava.LavaFire;
import net.nullcoil.nidalib.worldgen.biome.SubBiomes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nidalib implements ModInitializer {
	public static final String MOD_ID = "nidalib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SubBiomes.init();
		LavaFire.init();
		LOGGER.info("Finished initializing Nidalib");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
