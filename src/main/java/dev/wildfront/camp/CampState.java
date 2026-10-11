package dev.wildfront.camp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * What is known about a bandit camp whose guards have been spawned. Camps nobody has come near
 * have no state at all.
 *
 * @param center      middle of the camp at ground level; identifies the camp
 * @param guardsLeft  guards that are still alive
 * @param activatedAt overworld game time at which the guards were spawned
 */
public record CampState(BlockPos center, int guardsLeft, long activatedAt) {
	public static final Codec<CampState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("center").forGetter(CampState::center),
			Codec.INT.fieldOf("guards_left").forGetter(CampState::guardsLeft),
			Codec.LONG.fieldOf("activated_at").forGetter(CampState::activatedAt)
	).apply(instance, CampState::new));

	/**
	 * A cleared camp stays cleared: its guards never come back.
	 */
	public boolean cleared() {
		return guardsLeft <= 0;
	}

	public CampState withOneGuardLess() {
		return new CampState(center, Math.max(0, guardsLeft - 1), activatedAt);
	}
}
