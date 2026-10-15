package dev.wildfront.dungeon;

import java.util.ArrayList;
import java.util.List;

import dev.wildfront.registry.WildfrontAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The saved state of every dungeon in a level, kept with the level. A dungeon is known by its
 * lowest north-west corner, which no two dungeons share.
 */
public final class DungeonStates {
	private DungeonStates() {
	}

	public static List<DungeonState> all(ServerLevel level) {
		return level.getAttachedOrElse(WildfrontAttachments.DUNGEONS, List.of());
	}

	/**
	 * @return the dungeon's state, or a fresh one if nobody has touched it
	 */
	public static DungeonState of(ServerLevel level, BlockPos origin) {
		return all(level).stream().filter(state -> state.origin().equals(origin)).findFirst().orElseGet(() -> DungeonState.untouched(origin));
	}

	public static void save(ServerLevel level, DungeonState state) {
		List<DungeonState> states = new ArrayList<>(all(level));
		states.removeIf(other -> other.origin().equals(state.origin()));
		states.add(state);
		level.setAttached(WildfrontAttachments.DUNGEONS, List.copyOf(states));
	}

	/**
	 * Forgets a dungeon's state; its blocks stay as they are.
	 */
	public static void forget(ServerLevel level, BlockPos origin) {
		List<DungeonState> states = new ArrayList<>(all(level));

		if (states.removeIf(state -> state.origin().equals(origin))) {
			level.setAttached(WildfrontAttachments.DUNGEONS, List.copyOf(states));
		}
	}
}
