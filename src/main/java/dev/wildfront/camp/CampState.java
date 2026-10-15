package dev.wildfront.camp;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * What is known about a bandit camp whose guards have been spawned. Camps nobody has come near
 * have no state at all.
 *
 * @param center      middle of the camp at ground level; identifies the camp
 * @param guardsLeft  guards that are still alive, the chief among them
 * @param activatedAt overworld game time at which the guards were spawned
 * @param chief       whether the camp is led by a chief
 * @param stash       the chief's stash, which stays barred until the camp is cleared
 */
public record CampState(BlockPos center, int guardsLeft, long activatedAt, boolean chief, Optional<BlockPos> stash) {
	// The last two fields were added in 0.5.0; states saved before read as camps without a chief.
	public static final Codec<CampState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("center").forGetter(CampState::center),
			Codec.INT.fieldOf("guards_left").forGetter(CampState::guardsLeft),
			Codec.LONG.fieldOf("activated_at").forGetter(CampState::activatedAt),
			Codec.BOOL.optionalFieldOf("chief", false).forGetter(CampState::chief),
			BlockPos.CODEC.optionalFieldOf("stash").forGetter(CampState::stash)
	).apply(instance, CampState::new));

	public CampState(BlockPos center, int guardsLeft, long activatedAt) {
		this(center, guardsLeft, activatedAt, false, Optional.empty());
	}

	/**
	 * A cleared camp stays cleared: its guards never come back.
	 */
	public boolean cleared() {
		return guardsLeft <= 0;
	}

	public CampState withOneGuardLess() {
		return new CampState(center, Math.max(0, guardsLeft - 1), activatedAt, chief, stash);
	}

	/**
	 * @return whether the block at {@code pos} is this camp's stash and still barred
	 */
	public boolean bars(BlockPos pos) {
		return !cleared() && stash.isPresent() && stash.get().equals(pos);
	}
}
