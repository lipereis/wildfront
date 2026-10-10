package dev.wildfront;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Wildfront implements ModInitializer {
	public static final String MOD_ID = "wildfront";

	public static final Logger LOGGER = LoggerFactory.getLogger("Wildfront");

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");

		LOGGER.info("[Wildfront] Initialized (version {}, environment {})",
				version, FabricLoader.getInstance().getEnvironmentType());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
