package dev.wildfront.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.wildfront.Wildfront;
import dev.wildfront.registry.WildfrontAttachments;
import dev.wildfront.registry.WildfrontLootTables;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places forgotten caches and looks after them until they are found, destroyed or expire.
 * The list of active caches is stored on the level, so it survives restarts.
 */
public final class DiscoveryManager {
	/**
	 * Caches are looked at every this many ticks. Each look costs one block entity lookup per cache
	 * in a loaded chunk and nothing when there are no caches.
	 */
	static final int TICK_INTERVAL = 40;

	/**
	 * The chime plays on every this many looks, the particles on every look.
	 */
	static final int SOUND_EVERY = 3;

	static final int FOUND_MESSAGE_RADIUS = 8;

	static final int MAX_TRACKED = 256;

	private static long looks;

	private DiscoveryManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DiscoveryManager::onEndTick);
	}

	private static void onEndTick(MinecraftServer server) {
		if (server.getTickCount() % TICK_INTERVAL != 0) {
			return;
		}

		// Caches are only ever placed in the overworld.
		ServerLevel level = server.overworld();
		tick(level, level.getGameTime());
	}

	public static List<ActiveCache> activeCaches(ServerLevel level) {
		return level.getAttachedOrElse(WildfrontAttachments.ACTIVE_CACHES, List.of());
	}

	/**
	 * @return whether fewer than {@code maxActive} caches are waiting to be found. Expired caches in
	 *         unloaded chunks stay tracked for cleanup but do not count, so they cannot block new ones.
	 */
	public static boolean hasCapacity(ServerLevel level, long now, int maxActive) {
		return activeCaches(level).stream().filter(cache -> cache.expiresAt() > now).count() < maxActive;
	}

	/**
	 * Places an unopened cache with its loot table at {@code pos} and starts tracking it.
	 *
	 * @param pos       a position accepted by {@link CacheSiteFinder}
	 * @param expiresAt game time at which the cache is removed if nobody opened it
	 * @return false if the block could not be placed; nothing is tracked in that case
	 */
	public static boolean place(ServerLevel level, BlockPos pos, long expiresAt) {
		BlockState ground = level.getBlockState(pos.below());

		if (!level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP))
				|| !(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) {
			Wildfront.LOGGER.warn("[Wildfront] Could not place a forgotten cache at {}", pos.toShortString());
			return false;
		}

		barrel.setLootTable(WildfrontLootTables.FORGOTTEN_CACHE, level.getRandom().nextLong());
		barrel.setChanged();

		List<ActiveCache> caches = new ArrayList<>(activeCaches(level));

		// Only reachable if many expired caches sit in chunks that are never loaded again.
		while (caches.size() >= MAX_TRACKED) {
			Wildfront.LOGGER.warn("[Wildfront] Too many tracked caches; no longer tracking the one at {}", caches.remove(0).pos().toShortString());
		}

		caches.add(new ActiveCache(pos.immutable(), expiresAt, Optional.of(ground)));
		level.setAttached(WildfrontAttachments.ACTIVE_CACHES, List.copyOf(caches));

		Wildfront.LOGGER.info("[Wildfront] Forgotten cache placed at {} (expires at game time {}, {} active)",
				pos.toShortString(), expiresAt, caches.size());
		return true;
	}

	/**
	 * Updates every tracked cache whose chunk is loaded: finishes the ones that were opened or
	 * destroyed, removes the expired ones and makes the rest noticeable to nearby players.
	 */
	public static void tick(ServerLevel level, long now) {
		List<ActiveCache> caches = activeCaches(level);

		if (caches.isEmpty()) {
			return;
		}

		looks++;
		List<ActiveCache> remaining = new ArrayList<>(caches.size());

		for (ActiveCache cache : caches) {
			if (!level.isLoaded(cache.pos()) || update(level, cache, now)) {
				remaining.add(cache);
			}
		}

		if (remaining.size() != caches.size()) {
			level.setAttached(WildfrontAttachments.ACTIVE_CACHES, List.copyOf(remaining));
		}
	}

	/**
	 * @return true if the cache stays active
	 */
	private static boolean update(ServerLevel level, ActiveCache cache, long now) {
		BlockPos pos = cache.pos();

		if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) {
			// Broken by a player, an explosion or anything else. Breaking a barrel drops its loot,
			// so only the ground is left to look after.
			restoreGround(level, cache);
			Wildfront.LOGGER.info("[Wildfront] Forgotten cache at {} is gone", pos.toShortString());
			return false;
		}

		// Opening a container generates its loot and clears the loot table.
		if (barrel.getLootTable() == null) {
			onFound(level, pos);
			return false;
		}

		if (now >= cache.expiresAt()) {
			expire(level, barrel, pos);
			restoreGround(level, cache);
			return false;
		}

		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5, 8, 0.1, 1.0, 0.1, 0.01);

		if (looks % SOUND_EVERY == 0) {
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 2.0F, 0.7F);
		}

		return true;
	}

	/**
	 * Grass and mycelium turn to dirt while a solid block such as the cache stands on them. Once
	 * the cache is gone, puts the original block back so that no bare patch marks the spot. Only
	 * acts if the ground is exactly that dirt and nothing has been put where the cache stood.
	 */
	private static void restoreGround(ServerLevel level, ActiveCache cache) {
		BlockPos groundPos = cache.pos().below();
		BlockState original = cache.ground().orElse(null);

		if (original != null
				&& (original.is(Blocks.GRASS_BLOCK) || original.is(Blocks.MYCELIUM))
				&& level.getBlockState(groundPos).is(Blocks.DIRT)
				&& level.getBlockState(cache.pos()).isAir()) {
			level.setBlockAndUpdate(groundPos, original);
		}
	}

	private static void onFound(ServerLevel level, BlockPos pos) {
		Component message = Component.translatableWithFallback("event.wildfront.mysterious_discovery.found",
				"You uncovered a forgotten cache.").withStyle(ChatFormatting.LIGHT_PURPLE);

		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().closerThan(pos, FOUND_MESSAGE_RADIUS)) {
				player.sendOverlayMessage(message);
			}
		}

		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
		Wildfront.LOGGER.info("[Wildfront] Forgotten cache at {} was found", pos.toShortString());
	}

	private static void expire(ServerLevel level, BarrelBlockEntity barrel, BlockPos pos) {
		// Without a loot table the barrel is empty, so removing it drops nothing.
		barrel.setLootTable(null);
		level.removeBlock(pos, false);
		level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.02);
		Wildfront.LOGGER.info("[Wildfront] Forgotten cache at {} expired unopened and was removed", pos.toShortString());
	}
}
