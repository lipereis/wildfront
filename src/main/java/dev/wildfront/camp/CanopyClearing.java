package dev.wildfront.camp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Keeps the sky open above bandit camps. World generation builds a camp before it grows the trees
 * around it, so in a forest the crowns of neighbouring trees close over the camp and hang down
 * into it. Nothing can be done about that while the camp is built; instead the leaves are removed
 * once a chunk is finished, when no tree can reach into it any more.
 *
 * <p>A tree that this leaves bare is removed with its trunk. No chunk is loaded for any of this:
 * what is not loaded yet is dealt with when it is finished in turn. Only newly generated chunks
 * are handled.
 */
public final class CanopyClearing {
	/** Blocks around the camp that are cleared as well, so that it does not stand in a shaft. */
	static final int MARGIN = 1;

	/** Height above the camp's ground that is cleared; taller than any tree that grows beside one. */
	static final int HEIGHT = 32;

	/** How far below the camp's ground a trunk beside it may start, where the terrain falls away. */
	static final int BELOW = 3;

	/** Leaves are looked for this far around the top of a trunk. */
	static final int CROWN_RADIUS = 2;

	/** A tree beside the camp with fewer leaves left than this is removed. */
	static final int MIN_CROWN = 6;

	/** The blocks a camp's ground layer is made of. No tree grows on them. */
	private static final Set<Block> CAMP_GROUND = Set.of(Blocks.PACKED_MUD, Blocks.GRAVEL);

	private record Pending(ResourceKey<Level> dimension, ChunkPos chunk) {
	}

	/** Newly generated chunks with structures, waiting for the end of the tick they appeared in. */
	private static final Set<Pending> PENDING = new LinkedHashSet<>();

	private CanopyClearing() {
	}

	public static void register() {
		// Not cleared right here: changing blocks while a chunk is being handed over is asking
		// for trouble, and the camp reaches into neighbouring chunks.
		ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> {
			if (!chunk.getAllReferences().isEmpty()) {
				PENDING.add(new Pending(level.dimension(), chunk.getPos()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CanopyClearing::clearPending);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	private static void clearPending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		List<Pending> chunks = new ArrayList<>(PENDING);
		PENDING.clear();

		for (Pending pending : chunks) {
			ServerLevel level = server.getLevel(pending.dimension());

			if (level != null && level.getChunkSource().getChunkNow(pending.chunk().x(), pending.chunk().z()) != null) {
				clearIn(level, pending.chunk());
			}
		}
	}

	/**
	 * Clears the sky above every bandit camp near the chunk. The whole camp is gone over each
	 * time one of the chunks around it is finished, as far as its chunks are loaded by then, so
	 * that the last of them sees it complete.
	 *
	 * @return the number of blocks removed
	 */
	public static int clearIn(ServerLevel level, ChunkPos chunk) {
		Holder<Structure> camp = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.BANDIT_CAMP).orElse(null);

		if (camp == null) {
			return 0;
		}

		int removed = 0;

		for (StructureStart start : level.structureManager().startsForStructure(chunk.x(), chunk.z(), camp.value())) {
			if (start.isValid() && !start.getPieces().isEmpty()) {
				removed += clearAbove(level, start.getPieces().get(0).getBoundingBox());
			}
		}

		return removed;
	}

	/**
	 * Removes leaves and vines above a camp and one block around it, from just above its ground
	 * upwards, with the snow that lay on them. Then removes the trees this left bare: those that
	 * took root inside the camp, on a spot where the camp kept the terrain, and those beside it
	 * that have hardly a leaf left. Blocks in chunks that are not loaded are left alone.
	 *
	 * @param camp the blocks the camp occupies
	 * @return the number of blocks removed
	 */
	public static int clearAbove(ServerLevel level, BoundingBox camp) {
		int minY = camp.minY() + 1;
		int maxY = Math.min(camp.minY() + HEIGHT, level.getMaxY());
		int removed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = camp.minX() - MARGIN; x <= camp.maxX() + MARGIN; x++) {
			for (int z = camp.minZ() - MARGIN; z <= camp.maxZ() + MARGIN; z++) {
				if (!isLoaded(level, x, z)) {
					continue;
				}

				boolean removedBelow = false;

				for (int y = minY; y <= maxY; y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));
					boolean remove = isFoliage(state) || (removedBelow && state.is(Blocks.SNOW));

					if (remove) {
						remove(level, pos);
						removed++;
					}

					removedBelow = remove;
				}
			}
		}

		for (int x = camp.minX() - MARGIN; x <= camp.maxX() + MARGIN; x++) {
			for (int z = camp.minZ() - MARGIN; z <= camp.maxZ() + MARGIN; z++) {
				removed += removeBareTree(level, camp, x, z, maxY);
			}
		}

		return removed;
	}

	/**
	 * Removes the trunk in one column, and the leaves left around its top, if it is a tree that
	 * the clearing left bare.
	 */
	private static int removeBareTree(ServerLevel level, BoundingBox camp, int x, int z, int maxY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean inside = x >= camp.minX() && x <= camp.maxX() && z >= camp.minZ() && z <= camp.maxZ();

		// On the camp's own ground stands the camp: its palisade is made of logs too.
		if (!isLoaded(level, x, z) || (inside && CAMP_GROUND.contains(level.getBlockState(pos.set(x, camp.minY(), z)).getBlock()))) {
			return 0;
		}

		int bottom = Integer.MAX_VALUE;
		int top = Integer.MIN_VALUE;

		for (int y = camp.minY() - BELOW; y <= maxY; y++) {
			if (level.getBlockState(pos.set(x, y, z)).is(BlockTags.LOGS)) {
				bottom = Math.min(bottom, y);
				top = y;
			}
		}

		if (top == Integer.MIN_VALUE) {
			return 0;
		}

		// What is left of the crown: the leaves around the top of the trunk.
		List<BlockPos> crown = new ArrayList<>();

		for (int dx = -CROWN_RADIUS; dx <= CROWN_RADIUS; dx++) {
			for (int dz = -CROWN_RADIUS; dz <= CROWN_RADIUS; dz++) {
				if (!isLoaded(level, x + dx, z + dz)) {
					// Judged again when that chunk is finished.
					return 0;
				}

				for (int y = top - 3; y <= top + 2; y++) {
					if (isFoliage(level.getBlockState(pos.set(x + dx, y, z + dz)))) {
						crown.add(pos.immutable());
					}
				}
			}
		}

		if (!inside && crown.size() >= MIN_CROWN) {
			return 0;
		}

		int removed = 0;

		for (int y = bottom; y <= top + 1; y++) {
			BlockState state = level.getBlockState(pos.set(x, y, z));

			if (state.is(BlockTags.LOGS) || (y == top + 1 && state.is(Blocks.SNOW))) {
				remove(level, pos);
				removed++;
			}
		}

		for (BlockPos leaf : crown) {
			remove(level, leaf);
			removed++;
		}

		return removed;
	}

	private static boolean isFoliage(BlockState state) {
		return state.is(BlockTags.LEAVES) || state.is(Blocks.VINE);
	}

	private static boolean isLoaded(ServerLevel level, int x, int z) {
		return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
	}

	private static void remove(ServerLevel level, BlockPos pos) {
		// No shape updates: the neighbours may lie in a chunk that is not loaded.
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
