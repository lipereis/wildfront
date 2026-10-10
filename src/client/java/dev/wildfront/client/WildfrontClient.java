package dev.wildfront.client;

import dev.wildfront.Wildfront;

import net.fabricmc.api.ClientModInitializer;

public class WildfrontClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Wildfront.LOGGER.info("[Wildfront] Client initialized");
	}
}
