package dev.wildfront.event.type;

import java.util.List;
import java.util.Optional;

import dev.wildfront.config.DiscoveryConfig;
import dev.wildfront.config.EventConfig;
import dev.wildfront.discovery.CacheSiteFinder;
import dev.wildfront.discovery.CompassDirection;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.event.EventContext;
import dev.wildfront.event.EventSettings;
import dev.wildfront.event.WorldEvent;
import dev.wildfront.event.trigger.TimeOfDay;
import dev.wildfront.event.trigger.Weather;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * A forgotten cache appears on the surface some distance from a player exploring the overworld.
 * The player is told roughly where; a column of particles and a chime mark the spot up close.
 * Whoever opens the cache gets its loot. An unopened cache disappears after a while.
 */
public final class MysteriousDiscoveryEvent implements WorldEvent {
	public static final String ID = "mysterious_discovery";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public EventConfig defaultConfig() {
		return new EventConfig(true, 600, 0.05, 128, TimeOfDay.ANY, Weather.ANY);
	}

	@Override
	public boolean canTrigger(EventContext context) {
		return context.level().dimension() == Level.OVERWORLD;
	}

	@Override
	public Optional<Plan> plan(EventContext context) {
		ServerLevel level = context.level();
		DiscoveryConfig config = context.settings().discovery();

		if (!canTrigger(context) || !DiscoveryManager.hasCapacity(level, context.gameTime(), config.maxActive())) {
			return Optional.empty();
		}

		long expiresAt = context.gameTime() + EventSettings.ticks(config.lifetimeSeconds());

		return CacheSiteFinder.find(level, context.player().blockPosition(), config.minDistance(), config.maxDistance(), level.getRandom())
				.map(site -> participants -> appear(level, site, expiresAt, participants));
	}

	private static void appear(ServerLevel level, BlockPos site, long expiresAt, List<ServerPlayer> participants) {
		if (!DiscoveryManager.place(level, site, expiresAt)) {
			throw new IllegalStateException("Could not place the forgotten cache at " + site.toShortString());
		}

		Component overlay = Component.translatableWithFallback(
				"event.wildfront.mysterious_discovery.overlay", "Mysterious Discovery")
				.withStyle(ChatFormatting.LIGHT_PURPLE);

		for (ServerPlayer participant : participants) {
			BlockPos position = participant.blockPosition();
			CompassDirection direction = CompassDirection.fromOffset(site.getX() - position.getX(), site.getZ() - position.getZ());

			participant.sendSystemMessage(Component.translatableWithFallback(
					"event.wildfront.mysterious_discovery.message",
					"You sense that something unusual has appeared to the %s...",
					Component.translatableWithFallback(direction.translationKey(), direction.displayName()))
					.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
			participant.sendOverlayMessage(overlay);
		}
	}
}
