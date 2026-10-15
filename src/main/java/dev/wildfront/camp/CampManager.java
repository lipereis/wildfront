package dev.wildfront.camp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.wildfront.Wildfront;
import dev.wildfront.config.HostileCampConfig;
import dev.wildfront.discovery.SiteOffsets;
import dev.wildfront.entity.GuardEntity;
import dev.wildfront.registry.WildfrontAttachments;
import dev.wildfront.registry.WildfrontEntities;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Brings bandit camps to life. World generation only builds the camp; its guards are spawned here
 * the first time a player comes near, so that camps nobody visits cost nothing and the world's
 * difficulty and the configuration at that moment decide who is there.
 *
 * <p>Every guard is tagged with its camp. When the last one dies the camp is cleared for good.
 * Everything happens on the server thread.
 */
public final class CampManager {
	/** Scoreboard tag carried by every guard this mod spawned. */
	public static final String GUARD_TAG = "wildfront.guard";

	private static final String CAMP_TAG_PREFIX = "wildfront.camp.";

	/** Guards wander no further than this from the middle of their camp unless they chase someone. */
	static final int HOME_RADIUS = 12;

	static final int CLEARED_MESSAGE_RADIUS = 64;

	/** How far from the middle of its camp a chief's stash may stand. */
	static final int STASH_SEARCH = 5;

	/** How far the alarm of a guard that spotted a player carries. */
	public static final double ALARM_RADIUS = 32.0;

	/** The alarm bell of one camp rings at most this often, in ticks. */
	static final long ALARM_SOUND_INTERVAL = 200;

	private static final Map<BlockPos, Long> LAST_ALARM = new HashMap<>();
	private static boolean raisingAlarm;

	private CampManager() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onDeath(entity));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> mayUseStash(player, level, hit.getBlockPos()) ? InteractionResult.PASS : InteractionResult.FAIL);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> mayUseStash(player, level, pos));
	}

	public static List<CampState> states(ServerLevel level) {
		return level.getAttachedOrElse(WildfrontAttachments.CAMPS, List.of());
	}

	public static Optional<CampState> find(ServerLevel level, BlockPos center) {
		return states(level).stream().filter(state -> state.center().equals(center)).findFirst();
	}

	/**
	 * Called for one player on every check of the event manager. Looks for bandit camps in the
	 * loaded chunks around the player and activates those within reach. Costs one map lookup per
	 * chunk and does nothing in chunks without structures.
	 */
	public static void onPlayerCheck(ServerPlayer player, long now, HostileCampConfig config) {
		ServerLevel level = player.level();

		if (!config.enabled() || level.dimension() != Level.OVERWORLD) {
			return;
		}

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			vacate(level);
			return;
		}

		Holder<Structure> camp =level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.BANDIT_CAMP).orElse(null);

		if (camp == null) {
			return;
		}

		ChunkPos playerChunk = player.chunkPosition();
		int reach = config.activationRadius() / 16 + 1;

		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				// Never loads a chunk: a camp in an unloaded chunk is simply found on a later check.
				LevelChunk chunk = level.getChunkSource().getChunkNow(playerChunk.x() + dx, playerChunk.z() + dz);

				if (chunk == null || chunk.getAllStarts().isEmpty()) {
					continue;
				}

				StructureStart start = chunk.getStartForStructure(camp.value());

				if (start == null || !start.isValid()) {
					continue;
				}

				BlockPos center = centerOf(start);

				if (center.closerThan(player.blockPosition(), config.activationRadius())) {
					activate(level, center, now, config);
				}
			}
		}
	}

	/**
	 * @return the middle of the camp, one block above its ground layer: where its campfire stands
	 */
	public static BlockPos centerOf(StructureStart start) {
		// Not the start's own bounding box: for structures that blend into the terrain the game
		// pads that box by 12 blocks in every direction, downwards included.
		BoundingBox box = start.getPieces().isEmpty() ? start.getBoundingBox() : start.getPieces().get(0).getBoundingBox();
		return new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ());
	}

	/**
	 * Spawns the guards of the camp around {@code center}, unless the camp already has a state
	 * (occupied or cleared), the world is peaceful, or the server-wide limit on guards would be
	 * exceeded. In the last two cases nothing is recorded and a later check tries again.
	 *
	 * @return the new state if guards were spawned
	 */
	public static Optional<CampState> activate(ServerLevel level, BlockPos center, long now, HostileCampConfig config) {
		if (find(level, center).isPresent() || level.getDifficulty() == Difficulty.PEACEFUL) {
			return Optional.empty();
		}

		// Guards of a camp that was vacated may have outlived it in a chunk that was not loaded.
		// They take up their post again; nobody is added to them.
		List<? extends Mob> survivors = guardsOf(level, center);

		if (!survivors.isEmpty()) {
			boolean chief = survivors.stream().anyMatch(guard -> guard instanceof GuardEntity g && g.isChief());
			Optional<BlockPos> stash = chief ? findStash(level, center) : Optional.empty();
			return Optional.of(record(level, new CampState(center.immutable(), survivors.size(), now, chief, stash)));
		}

		List<GuardRoster.Role> roster = GuardRoster.pick(config.minGuards(), config.maxGuards(), strength(level.getDifficulty()), level.getRandom()::nextDouble);
		// A chief needs a stash to guard; every bandit camp layout has one, a camp built by hand may not.
		Optional<BlockPos> stash = level.getRandom().nextDouble() < config.chiefChance() ? findStash(level, center) : Optional.empty();
		int living = states(level).stream().mapToInt(CampState::guardsLeft).sum();

		if (living + roster.size() + (stash.isPresent() ? 1 : 0) > config.maxLivingGuards()) {
			return Optional.empty();
		}

		int spawned = 0;

		for (GuardRoster.Role role : roster) {
			if (spawnGuard(level, center, role, false)) {
				spawned++;
			}
		}

		boolean chief = stash.isPresent() && spawned > 0 && spawnGuard(level, center, GuardRoster.Role.BRUTE, true);

		if (spawned == 0) {
			Wildfront.LOGGER.warn("[Wildfront] Found no room for guards at the bandit camp at {}; trying again later", center.toShortString());
			return Optional.empty();
		}

		if (chief && level.getBlockEntity(stash.get()) instanceof RandomizableContainer container) {
			container.setLootTable(WildfrontLootTables.CHIEF_STASH, level.getRandom().nextLong());
		}

		return Optional.of(record(level, new CampState(center.immutable(), spawned + (chief ? 1 : 0), now, chief, chief ? stash : Optional.empty())));
	}

	/**
	 * @return the barrel of the camp around {@code center}, which a chief keeps his stash in
	 */
	static Optional<BlockPos> findStash(ServerLevel level, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-STASH_SEARCH, -1, -STASH_SEARCH), center.offset(STASH_SEARCH, 2, STASH_SEARCH))) {
			if (level.getBlockState(pos).is(Blocks.BARREL)) {
				return Optional.of(pos.immutable());
			}
		}

		return Optional.empty();
	}

	/**
	 * @return whether the block is the stash of a camp whose guards are still alive
	 */
	public static boolean isBarred(ServerLevel level, BlockPos pos) {
		return states(level).stream().anyMatch(state -> state.bars(pos));
	}

	/**
	 * Keeps players out of a chief's stash until the camp is cleared. Players in creative mode are
	 * not stopped.
	 */
	private static boolean mayUseStash(Player player, Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel) || player.isCreative() || player.isSpectator() || !isBarred(serverLevel, pos)) {
			return true;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback("camp.wildfront.stash_barred",
					"The chief's stash is barred. Defeat the camp first.").withStyle(ChatFormatting.RED));
		}

		return false;
	}

	/**
	 * Called when a guard first spots a player: every guard of its camp nearby that is not busy
	 * yet goes for that player too, and the camp's alarm sounds.
	 */
	public static void raiseAlarm(ServerLevel level, GuardEntity spotter, Player player) {
		BlockPos camp = campOf(spotter);

		if (camp == null || raisingAlarm) {
			return;
		}

		raisingAlarm = true;

		try {
			List<GuardEntity> others = level.getEntitiesOfClass(GuardEntity.class, spotter.getBoundingBox().inflate(ALARM_RADIUS),
					guard -> guard != spotter && guard.isAlive() && guard.getTarget() == null && camp.equals(campOf(guard)));
			others.forEach(guard -> guard.setTarget(player));

			Long last = LAST_ALARM.get(camp);
			long now = level.getGameTime();

			if (last == null || now - last >= ALARM_SOUND_INTERVAL) {
				LAST_ALARM.put(camp, now);
				level.playSound(null, spotter.getX(), spotter.getY(), spotter.getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0F, 0.8F);
			}
		} finally {
			raisingAlarm = false;
		}
	}

	/**
	 * @return whether two entities guard the same camp; guards that belong to no camp, such as
	 *         summoned ones, count as one camp
	 */
	public static boolean sameCamp(Entity a, Entity b) {
		return Objects.equals(campOf(a), campOf(b));
	}

	private static CampState record(ServerLevel level, CampState state) {
		List<CampState> states = new ArrayList<>(states(level));
		states.add(state);
		level.setAttached(WildfrontAttachments.CAMPS, List.copyOf(states));

		Wildfront.LOGGER.info("[Wildfront] Bandit camp at {} is occupied by {} guard(s) (game time {})", state.center().toShortString(),
				state.guardsLeft(), state.activatedAt());
		return state;
	}

	/**
	 * Forgets every camp that still has guards. Called while the world is peaceful: the game
	 * removes the guards without their dying, so their camps would otherwise count as occupied
	 * forever. Such a camp is occupied again when a player comes near on a higher difficulty.
	 * Cleared camps stay cleared.
	 *
	 * @return the number of camps forgotten
	 */
	public static int vacate(ServerLevel level) {
		List<CampState> states = states(level);
		List<CampState> kept = states.stream().filter(CampState::cleared).toList();
		int vacated = states.size() - kept.size();

		if (vacated > 0) {
			level.setAttached(WildfrontAttachments.CAMPS, kept);
			Wildfront.LOGGER.info("[Wildfront] {} bandit camp(s) stand empty while the world is peaceful", vacated);
		}

		return vacated;
	}

	private static List<? extends Mob> guardsOf(ServerLevel level, BlockPos center) {
		String tag = campTag(center);
		return level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob.isAlive() && mob.entityTags().contains(tag));
	}

	private static GuardRoster.Strength strength(Difficulty difficulty) {
		return switch (difficulty) {
			case HARD -> GuardRoster.Strength.HIGH;
			case NORMAL -> GuardRoster.Strength.MEDIUM;
			default -> GuardRoster.Strength.LOW;
		};
	}

	private static boolean spawnGuard(ServerLevel level, BlockPos center, GuardRoster.Role role, boolean chief) {
		BlockPos spot = findGuardSpot(level, center, level.getRandom());
		GuardEntity guard = spot == null ? null : WildfrontEntities.GUARD.create(level, EntitySpawnReason.STRUCTURE);

		if (guard == null) {
			return false;
		}

		guard.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		guard.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.STRUCTURE, null);
		guard.equip(role == GuardRoster.Role.BRUTE ? GuardEntity.Role.MELEE : GuardEntity.Role.RANGED);

		if (chief) {
			guard.makeChief();
		}
		guard.setPersistenceRequired();
		guard.setHomeTo(center, HOME_RADIUS);
		guard.addTag(GUARD_TAG);
		guard.addTag(campTag(center));
		return level.addFreshEntity(guard);
	}

	/**
	 * @return a free spot on the ground a few blocks from the middle of the camp, or null
	 */
	private static BlockPos findGuardSpot(ServerLevel level, BlockPos center, RandomSource random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			int[] offset = SiteOffsets.ring(random::nextDouble, 2, 4);
			int x = center.getX() + offset[0];
			int z = center.getZ() + offset[1];
			BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);

			// On the camp's own level, not on top of a tent or down a hole beside it.
			if (Math.abs(spot.getY() - center.getY()) > 1) {
				continue;
			}

			BlockState feet = level.getBlockState(spot);
			BlockState head = level.getBlockState(spot.above());

			if (level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)
					&& (feet.isAir() || feet.canBeReplaced()) && feet.getFluidState().isEmpty()
					&& (head.isAir() || head.canBeReplaced()) && head.getFluidState().isEmpty()) {
				return spot;
			}
		}

		return null;
	}

	private static String campTag(BlockPos center) {
		return CAMP_TAG_PREFIX + center.getX() + "_" + center.getY() + "_" + center.getZ();
	}

	public static BlockPos campOf(Entity guard) {
		for (String tag : guard.entityTags()) {
			if (tag.startsWith(CAMP_TAG_PREFIX)) {
				String[] parts = tag.substring(CAMP_TAG_PREFIX.length()).split("_");

				try {
					return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
				} catch (RuntimeException e) {
					return null;
				}
			}
		}

		return null;
	}

	private static void onDeath(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || !entity.entityTags().contains(GUARD_TAG)) {
			return;
		}

		BlockPos center = campOf(entity);
		CampState before = center == null ? null : find(level, center).orElse(null);

		if (before == null || before.cleared()) {
			return;
		}

		CampState after = before.withOneGuardLess();
		List<CampState> states = new ArrayList<>(states(level));
		states.set(states.indexOf(before), after);
		level.setAttached(WildfrontAttachments.CAMPS, List.copyOf(states));

		if (after.cleared()) {
			onCleared(level, center);
		}
	}

	private static void onCleared(ServerLevel level, BlockPos center) {
		boolean chief = find(level, center).map(CampState::chief).orElse(false);
		Component message = (chief
				? Component.translatableWithFallback("camp.wildfront.cleared.chief", "The chief has fallen. The camp's stash is yours.")
				: Component.translatableWithFallback("camp.wildfront.cleared", "The camp falls silent. Its guards are gone for good."))
				.withStyle(ChatFormatting.GOLD);
		Component overlay = Component.translatableWithFallback("camp.wildfront.cleared.overlay", "Camp cleared").withStyle(ChatFormatting.GOLD);

		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().closerThan(center, CLEARED_MESSAGE_RADIUS)) {
				player.sendSystemMessage(message);
				player.sendOverlayMessage(overlay);
			}
		}

		Wildfront.LOGGER.info("[Wildfront] Bandit camp at {} was cleared", center.toShortString());
	}

	/**
	 * Removes every loaded guard and forgets every camp, so camps can be activated again.
	 *
	 * @return {removed guards, forgotten camps}
	 */
	public static int[] reset(ServerLevel level) {
		List<? extends Mob> guards = level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob.entityTags().contains(GUARD_TAG));
		guards.forEach(Entity::discard);
		int camps = states(level).size();
		level.removeAttached(WildfrontAttachments.CAMPS);
		return new int[] {guards.size(), camps};
	}

	/**
	 * Removes the guards of one camp and forgets it. Used by tests to clean up after themselves.
	 */
	public static void forget(ServerLevel level, BlockPos center) {
		String tag = campTag(center);
		level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob.entityTags().contains(tag)).forEach(Entity::discard);
		List<CampState> states = new ArrayList<>(states(level));

		if (states.removeIf(state -> state.center().equals(center))) {
			level.setAttached(WildfrontAttachments.CAMPS, List.copyOf(states));
		}
	}
}
