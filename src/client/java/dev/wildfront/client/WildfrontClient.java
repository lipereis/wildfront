package dev.wildfront.client;

import dev.wildfront.Wildfront;
import dev.wildfront.registry.WildfrontEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class WildfrontClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(WildfrontEntities.GUARD, GuardRenderer::new);

		Wildfront.LOGGER.info("[Wildfront] Client initialized");
	}
}
