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
 * <p>Each chunk clears its own part of a camp, so no other chunk is touched or loaded. Only newly
 * generated chunks are handled.
 */
public final class CanopyClearing {
	/** Blocks around the camp that are cleared as well, so that it does not stand in a shaft. */
	static final int MARGIN = 1;

	/** Height above the camp's ground that is cleared; taller than any tree that grows beside one. */
	static final int HEIGHT = 32;

	private record Pending(ResourceKey<Level> dimension, ChunkPos chunk) {
	}

	/** Newly generated chunks with structures, waiting for the end of the tick they appeared in. */
	private static final Set<Pending> PENDING = new LinkedHashSet<>();

	private CanopyClearing() {
	}

	public static void register() {
		// Not cleared right here: changing blocks while a chunk is being handed over is asking
		// for trouble, and the camp's own data may sit in a neighbouring chunk.
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
	 * Removes the leaves above every bandit camp that reaches into the chunk, within that chunk.
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
				removed += clearAbove(level, start.getPieces().get(0).getBoundingBox(), chunk);
			}
		}

		return removed;
	}

	/**
	 * Removes leaves and vines above a camp, from just above its ground upwards, and the snow
	 * that lay on them.
	 *
	 * @param camp   the blocks the camp occupies
	 * @param within if not null, only blocks in this chunk are touched
	 * @return the number of blocks removed
	 */
	public static int clearAbove(ServerLevel level, BoundingBox camp, ChunkPos within) {
		int minX = camp.minX() - MARGIN;
		int maxX = camp.maxX() + MARGIN;
		int minZ = camp.minZ() - MARGIN;
		int maxZ = camp.maxZ() + MARGIN;

		if (within != null) {
			minX = Math.max(minX, within.getMinBlockX());
			maxX = Math.min(maxX, within.getMaxBlockX());
			minZ = Math.max(minZ, within.getMinBlockZ());
			maxZ = Math.min(maxZ, within.getMaxBlockZ());
		}

		int minY = camp.minY() + 1;
		int maxY = Math.min(camp.minY() + HEIGHT, level.getMaxY());
		int removed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				boolean removedBelow = false;

				for (int y = minY; y <= maxY; y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));
					boolean remove = state.is(BlockTags.LEAVES) || state.is(Blocks.VINE) || (removedBelow && state.is(Blocks.SNOW));

					if (remove) {
						// No shape updates: the neighbours may lie in a chunk that is not loaded.
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						removed++;
					}

					removedBelow = remove;
				}
			}
		}

		return removed;
	}
}
