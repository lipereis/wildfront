package dev.wildfront.event;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

import dev.wildfront.registry.WildfrontAttachments;

import net.minecraft.server.level.ServerPlayer;

/**
 * Cooldowns stored on the player, so they survive death, relogging and server restarts.
 */
public final class PlayerCooldowns implements CooldownTracker {
	private final ServerPlayer player;

	public PlayerCooldowns(ServerPlayer player) {
		this.player = player;
	}

	@Override
	public OptionalLong lastTrigger(String eventId) {
		Long last = player.getAttachedOrElse(WildfrontAttachments.EVENT_COOLDOWNS, Map.of()).get(eventId);
		return last == null ? OptionalLong.empty() : OptionalLong.of(last);
	}

	@Override
	public void recordTrigger(String eventId, long gameTime) {
		Map<String, Long> updated = new HashMap<>(player.getAttachedOrElse(WildfrontAttachments.EVENT_COOLDOWNS, Map.of()));
		updated.put(eventId, gameTime);
		player.setAttached(WildfrontAttachments.EVENT_COOLDOWNS, Map.copyOf(updated));
	}

	public void clear() {
		player.removeAttached(WildfrontAttachments.EVENT_COOLDOWNS);
	}
}
