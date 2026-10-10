package dev.wildfront.discovery;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A forgotten cache that was placed in the world and has not been found or removed yet.
 *
 * @param expiresAt overworld game time at which an unopened cache is removed again
 * @param ground    the block the cache was placed on, as it was at that moment; empty for caches
 *                  saved by versions that did not record it
 */
public record ActiveCache(BlockPos pos, long expiresAt, Optional<BlockState> ground) {
	public static final Codec<ActiveCache> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(ActiveCache::pos),
			Codec.LONG.fieldOf("expires_at").forGetter(ActiveCache::expiresAt),
			BlockState.CODEC.optionalFieldOf("ground").forGetter(ActiveCache::ground)
	).apply(instance, ActiveCache::new));
}
