package dev.wildfront.event;

import dev.wildfront.config.WildfrontConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything an event needs to check its conditions and run for one player.
 *
 * @param gameTime overworld game time, the clock used for cooldowns
 */
public record EventContext(ServerPlayer player, ServerLevel level, long gameTime, WildfrontConfig config) {
}
