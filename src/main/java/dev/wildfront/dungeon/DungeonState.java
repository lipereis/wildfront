package dev.wildfront.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * What is known about a dungeon somebody has interacted with. Dungeons nobody has touched have no
 * state; everything else about them is in their blocks.
 *
 * @param origin     the lowest north-west corner of the dungeon; identifies it
 * @param progress   how far its puzzle has been solved
 * @param solved     whether its main chamber is open
 * @param secretOpen whether its secret chamber is open
 */
public record DungeonState(BlockPos origin, int progress, boolean solved, boolean secretOpen) {
	public static final Codec<DungeonState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("origin").forGetter(DungeonState::origin),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(DungeonState::progress),
			Codec.BOOL.optionalFieldOf("solved", false).forGetter(DungeonState::solved),
			Codec.BOOL.optionalFieldOf("secret_open", false).forGetter(DungeonState::secretOpen)
	).apply(instance, DungeonState::new));

	public static DungeonState untouched(BlockPos origin) {
		return new DungeonState(origin.immutable(), 0, false, false);
	}

	public DungeonState withProgress(int progress) {
		return new DungeonState(origin, progress, solved, secretOpen);
	}

	public DungeonState asSolved() {
		return new DungeonState(origin, progress, true, secretOpen);
	}

	public DungeonState withSecretOpen() {
		return new DungeonState(origin, progress, solved, true);
	}
}
