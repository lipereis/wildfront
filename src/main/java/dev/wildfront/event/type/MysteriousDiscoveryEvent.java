package dev.wildfront.event.type;

import dev.wildfront.config.WildfrontConfig;
import dev.wildfront.event.EventContext;
import dev.wildfront.event.EventSettings;
import dev.wildfront.event.WorldEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

/**
 * Tells a player who is out exploring the overworld that something unusual happened nearby.
 * This first version only notifies; the discovery itself arrives in a later milestone.
 */
public final class MysteriousDiscoveryEvent implements WorldEvent {
	public static final String ID = "mysterious_discovery";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public EventSettings settings(WildfrontConfig config) {
		WildfrontConfig.MysteriousDiscovery settings = config.mysteriousDiscovery();
		return EventSettings.ofSeconds(settings.enabled(), settings.cooldownSeconds(), settings.triggerChance());
	}

	@Override
	public boolean canTrigger(EventContext context) {
		if (context.level().dimension() != Level.OVERWORLD) {
			return false;
		}

		LevelData.RespawnData spawn = context.level().getRespawnData();

		// World spawn in another dimension: there is no meaningful distance to measure.
		if (spawn.dimension() != Level.OVERWORLD) {
			return true;
		}

		BlockPos position = context.player().blockPosition();
		long dx = position.getX() - spawn.pos().getX();
		long dz = position.getZ() - spawn.pos().getZ();
		long minDistance = context.config().mysteriousDiscovery().minDistanceFromSpawn();

		return dx * dx + dz * dz >= minDistance * minDistance;
	}

	@Override
	public void trigger(EventContext context) {
		context.player().sendSystemMessage(Component.translatableWithFallback(
				"event.wildfront.mysterious_discovery.message",
				"You sense that something unusual has happened nearby...")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		context.player().sendOverlayMessage(Component.translatableWithFallback(
				"event.wildfront.mysterious_discovery.overlay",
				"Mysterious Discovery")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
	}
}
