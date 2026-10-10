package dev.wildfront.event;

import java.util.List;

import dev.wildfront.config.EventConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything an event needs to check its conditions and run.
 *
 * @param player       the player the event triggers for
 * @param gameTime     overworld game time, the clock used for cooldowns
 * @param config       the settings of the event being evaluated
 * @param participants everyone who experiences the event: {@code player} plus nearby players who
 *                     share it. While conditions are being checked it contains only {@code player}.
 */
public record EventContext(ServerPlayer player, ServerLevel level, long gameTime, EventConfig config, List<ServerPlayer> participants) {
}
