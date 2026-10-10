package dev.wildfront.event;

import dev.wildfront.config.EventConfig;
import dev.wildfront.config.WildfrontConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything an event needs to check its conditions and plan an occurrence.
 *
 * @param player   the player the event triggers for
 * @param gameTime overworld game time, the clock used for cooldowns
 * @param config   the settings of the event being evaluated
 * @param settings the complete configuration, for event-specific sections
 */
public record EventContext(ServerPlayer player, ServerLevel level, long gameTime, EventConfig config, WildfrontConfig settings) {
}
