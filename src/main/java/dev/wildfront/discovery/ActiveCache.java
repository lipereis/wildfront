package dev.wildfront.discovery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * A forgotten cache that was placed in the world and has not been found or removed yet.
 *
 * @param expiresAt overworld game time at which an unopened cache is removed again
 */
public record ActiveCache(BlockPos pos, long expiresAt) {
	public static final Codec<ActiveCache> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(ActiveCache::pos),
			Codec.LONG.fieldOf("expires_at").forGetter(ActiveCache::expiresAt)
	).apply(instance, ActiveCache::new));
}
