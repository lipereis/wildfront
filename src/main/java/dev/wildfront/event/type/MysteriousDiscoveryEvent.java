package dev.wildfront.event.type;

import dev.wildfront.config.EventConfig;
import dev.wildfront.event.EventContext;
import dev.wildfront.event.WorldEvent;
import dev.wildfront.event.trigger.TimeOfDay;
import dev.wildfront.event.trigger.Weather;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Tells players who are out exploring the overworld that something unusual happened nearby.
 * This first version only notifies; the discovery itself arrives in a later milestone.
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
	public void trigger(EventContext context) {
		Component message = Component.translatableWithFallback(
				"event.wildfront.mysterious_discovery.message",
				"You sense that something unusual has happened nearby...")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
		Component overlay = Component.translatableWithFallback(
				"event.wildfront.mysterious_discovery.overlay",
				"Mysterious Discovery")
				.withStyle(ChatFormatting.LIGHT_PURPLE);

		for (ServerPlayer participant : context.participants()) {
			participant.sendSystemMessage(message);
			participant.sendOverlayMessage(overlay);
		}
	}
}
