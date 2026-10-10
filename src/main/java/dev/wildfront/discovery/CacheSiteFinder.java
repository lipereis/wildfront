package dev.wildfront.discovery;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds a surface position near a player where a cache can stand. Only already loaded chunks are
 * looked at, and each attempt costs one heightmap lookup and two block state reads.
 */
public final class CacheSiteFinder {
	static final int ATTEMPTS = 12;

	/**
	 * A site further above or below the player than this is likely on a cliff top or the floor of a
	 * ravine and frustrating to reach.
	 */
	static final int MAX_HEIGHT_DIFFERENCE = 24;

	private CacheSiteFinder() {
	}

	/**
	 * @return the position the cache block itself would occupy, or empty if no attempt found one
	 */
	public static Optional<BlockPos> find(ServerLevel level, BlockPos origin, int minDistance, int maxDistance, RandomSource random) {
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			int[] offset = SiteOffsets.ring(random::nextDouble, minDistance, maxDistance);
			int x = origin.getX() + offset[0];
			int z = origin.getZ() + offset[1];

			if (!level.hasChunkAt(x, z)) {
				continue;
			}

			BlockPos site = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);

			if (isSuitable(level, site, origin)) {
				return Optional.of(site);
			}
		}

		return Optional.empty();
	}

	public static boolean isSuitable(ServerLevel level, BlockPos site, BlockPos origin) {
		if (site.getY() <= level.getMinY() || site.getY() >= level.getMaxY()) {
			return false;
		}

		if (Math.abs(site.getY() - origin.getY()) > MAX_HEIGHT_DIFFERENCE) {
			return false;
		}

		BlockPos groundPos = site.below();
		BlockState ground = level.getBlockState(groundPos);
		BlockState space = level.getBlockState(site);

		// Never replace anything but air or plants, and never stand in or on a fluid.
		return ground.isFaceSturdy(level, groundPos, Direction.UP)
				&& ground.getFluidState().isEmpty()
				&& (space.isAir() || space.canBeReplaced())
				&& space.getFluidState().isEmpty();
	}
}
