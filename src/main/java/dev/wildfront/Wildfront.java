package dev.wildfront;

import dev.wildfront.camp.CampManager;
import dev.wildfront.camp.CanopyClearing;
import dev.wildfront.command.WildfrontCommands;
import dev.wildfront.config.ConfigManager;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.event.EventManager;
import dev.wildfront.registry.WildfrontAttachments;
import dev.wildfront.registry.WildfrontEntities;
import dev.wildfront.registry.WildfrontEvents;
import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Wildfront implements ModInitializer {
	public static final String MOD_ID = "wildfront";

	public static final Logger LOGGER = LoggerFactory.getLogger("Wildfront");

	public static final EventManager EVENTS = new EventManager();

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");

		ConfigManager.reload();
		WildfrontAttachments.init();
		WildfrontStructures.init();
		WildfrontEntities.init();
		WildfrontEvents.init();
		EVENTS.register();
		DiscoveryManager.register();
		CampManager.register();
		CanopyClearing.register();
		WildfrontCommands.register();

		LOGGER.info("[Wildfront] Initialized (version {}, environment {})",
				version, FabricLoader.getInstance().getEnvironmentType());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
