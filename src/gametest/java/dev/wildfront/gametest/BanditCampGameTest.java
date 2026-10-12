package dev.wildfront.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;

import dev.wildfront.camp.CampManager;
import dev.wildfront.camp.CampState;
import dev.wildfront.camp.CanopyClearing;
import dev.wildfront.config.HostileCampConfig;
import dev.wildfront.registry.WildfrontAttachments;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Bandit camps on a real server: their data, their guards, and what happens when the guards die.
 */
public class BanditCampGameTest {
	private static final BlockPos CENTER = new BlockPos(3, 1, 3);

	private static HostileCampConfig config(int guards, int maxLiving) {
		return new HostileCampConfig(true, 48, guards, guards, maxLiving);
	}

	private static List<? extends Mob> guardsOf(ServerLevel level, BlockPos center) {
		return level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob.entityTags().contains(CampManager.GUARD_TAG)
				&& mob.hasHome() && mob.getHomePosition().equals(center));
	}

	/**
	 * Guards only spawn when the world is not peaceful; the test server's difficulty is not ours to
	 * assume.
	 */
	private static void ensureHostileWorld(ServerLevel level) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
		}
	}

	@GameTest
	public void worldgenDataIsRegistered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.BANDIT_CAMP).isPresent(),
				"structure wildfront:bandit_camp should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(WildfrontStructures.BANDIT_CAMPS).isPresent(),
				"structure set wildfront:bandit_camps should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).get(WildfrontStructures.BANDIT_CAMP_START_POOL).isPresent(),
				"template pool wildfront:bandit_camp/start should be registered");
		helper.succeed();
	}

	@GameTest(padding = 2)
	public void everyLayoutIsACampWithFireChestAndTarget(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);

		for (Identifier id : WildfrontStructures.BANDIT_CAMP_LAYOUTS) {
			StructureTemplate template = level.getStructureTemplateManager().get(id).orElse(null);
			helper.assertTrue(template != null, "template " + id + " should load");
			helper.assertTrue(template.getSize().equals(new Vec3i(9, 5, 9)), "template " + id + " should be 9x5x9 but was " + template.getSize());

			for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(8, 4, 8))) {
				level.removeBlock(pos, false);
			}

			helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2), "template " + id + " should be placed");

			int fires = 0;
			int lootChests = 0;
			int targets = 0;

			for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(8, 4, 8))) {
				BlockState state = level.getBlockState(pos);

				if (state.is(Blocks.CAMPFIRE)) {
					fires++;
					helper.assertTrue(state.getValue(CampfireBlock.LIT), "the fire of an occupied camp should burn");
					helper.assertTrue(pos.equals(origin.offset(4, 1, 4)), "the fire of " + id + " should be in the middle of the camp but is at " + pos.subtract(origin));
				}

				if (state.is(Blocks.TARGET)) {
					targets++;
				}

				if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && WildfrontLootTables.BANDIT_CAMP_SMALL.equals(chest.getLootTable())) {
					lootChests++;
					chest.unpackLootTable(null);
					helper.assertFalse(chest.isEmpty(), "the chest of " + id + " should generate loot");
					chest.clearContent();
				}

				if (pos.getY() == origin.getY() && !state.isAir()) {
					helper.assertFalse(state.is(BlockTags.SUPPORTS_VEGETATION), "the ground of " + id + " contains " + state.getBlock() + ", which trees can take root in");
				}
			}

			helper.assertTrue(fires == 1, id + " should have exactly one campfire but has " + fires);
			helper.assertTrue(lootChests == 1, id + " should have exactly one loot chest but has " + lootChests);
			helper.assertTrue(targets == 1, id + " should have exactly one target but has " + targets);

			for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(8, 4, 8))) {
				level.removeBlock(pos, false);
			}
		}

		helper.killAllEntities();
		helper.succeed();
	}

	/**
	 * Generates the camp the way world generation and {@code /place structure} do.
	 */
	@GameTest
	public void structureGeneratesAtGroundLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(4, 0, 4));
		String command = "place structure wildfront:bandit_camp " + center.getX() + " " + center.getY() + " " + center.getZ();

		try {
			level.getServer().getCommands().getDispatcher().execute(command, level.getServer().createCommandSourceStack().withSuppressedOutput());
		} catch (CommandSyntaxException e) {
			throw helper.assertionException("/" + command + " failed: " + e.getMessage());
		}

		// The camp starts at the corner of the chunk, on the test world's surface below the test areas.
		BlockPos start = new BlockPos(center.getX() & ~15, center.getY() - 1, center.getZ() & ~15);
		BlockPos fire = null;

		for (BlockPos pos : BlockPos.betweenClosed(start.offset(-11, -8, -11), start.offset(11, 0, 11))) {
			BlockState state = level.getBlockState(pos);

			if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
				fire = pos.immutable();
			}
		}

		helper.assertTrue(fire != null, "the generated bandit camp should contain its burning fire");
		int groundLevel = level.getChunkSource().getGenerator().getFirstFreeHeight(fire.getX(), fire.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level,
				level.getChunkSource().randomState());
		helper.assertTrue(fire.getY() == groundLevel, "the fire should stand at ground level " + groundLevel + " but is at " + fire.getY());
		helper.assertTrue(level.getBlockState(fire.below()).is(Blocks.PACKED_MUD), "the camp's ground layer should be under its fire");

		// Remove the fire so its light and smoke do not linger in the kept part of the test world.
		level.removeBlock(fire, false);
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void guardsSpawnOnceAndTheCampIsClearedWhenTheyDie(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ensureHostileWorld(level);
		GameTestSupport.buildFloor(helper);
		BlockPos center = helper.absolutePos(CENTER);
		long now = level.getGameTime();

		try {
			Optional<CampState> activated = CampManager.activate(level, center, now, config(3, 40));
			helper.assertTrue(activated.isPresent(), "guards should be spawned at a camp nobody visited before");
			List<? extends Mob> guards = guardsOf(level, center);
			helper.assertTrue(guards.size() == activated.get().guardsLeft() && !guards.isEmpty(),
					"the camp records " + activated.get().guardsLeft() + " guard(s) but " + guards.size() + " exist");
			helper.assertTrue(guards.size() == 3, "three guards should fit on an open floor but " + guards.size() + " were spawned");
			helper.assertTrue(guards.stream().filter(guard -> guard.getType() == EntityTypes.VINDICATOR).count() == 1
					&& guards.stream().filter(guard -> guard.getType() == EntityTypes.PILLAGER).count() == 2, "three guards should be two pillagers and one vindicator");

			for (Mob guard : guards) {
				helper.assertTrue(guard.isPersistenceRequired(), "a guard must not despawn");
				helper.assertTrue(guard.getHomeRadius() > 0 && guard.blockPosition().closerThan(center, 6), "a guard should start near the middle of its camp");
				helper.assertFalse(guard.getMainHandItem().isEmpty(), "a guard should be armed");
				helper.assertFalse(guard instanceof PatrollingMonster patroller && patroller.isPatrolLeader(), "a guard must not be a patrol leader, whose banner starts raids");
			}

			helper.assertTrue(CampManager.activate(level, center, now + 20, config(3, 40)).isEmpty(), "an occupied camp should not be activated again");
			helper.assertTrue(guardsOf(level, center).size() == 3, "a second visit must not spawn more guards");

			guards.get(0).kill(level);
			helper.assertTrue(CampManager.find(level, center).orElseThrow().guardsLeft() == 2, "a guard's death should be counted");
			helper.assertFalse(CampManager.find(level, center).orElseThrow().cleared(), "a camp with guards left is not cleared");

			guards.get(1).kill(level);
			guards.get(2).kill(level);
			helper.assertTrue(CampManager.find(level, center).orElseThrow().cleared(), "a camp whose guards are all dead should be cleared");

			helper.assertTrue(CampManager.activate(level, center, now + 40, config(3, 40)).isEmpty(), "a cleared camp must stay cleared");
			helper.assertTrue(guardsOf(level, center).stream().noneMatch(Mob::isAlive), "no guards should come back to a cleared camp");
		} finally {
			CampManager.forget(level, center);
			helper.killAllEntities();
		}

		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void campVacatedInPeacefulIsOccupiedAgainWithoutExtraGuards(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ensureHostileWorld(level);
		GameTestSupport.buildFloor(helper);
		BlockPos center = helper.absolutePos(CENTER);
		BlockPos clearedCenter = center.above(40);
		long now = level.getGameTime();
		List<CampState> others = CampManager.states(level);

		try {
			helper.assertTrue(CampManager.activate(level, center, now, config(3, 200)).isPresent(), "guards should be spawned");
			// A camp that was cleared earlier, to see that it is left alone.
			List<CampState> withCleared = new ArrayList<>(CampManager.states(level));
			withCleared.add(new CampState(clearedCenter, 0, now));
			level.setAttached(WildfrontAttachments.CAMPS, List.copyOf(withCleared));

			// What the mod does on a check while the world is peaceful. The world of the tests is
			// not really set to peaceful, as that would remove the guards of the other tests.
			helper.assertTrue(CampManager.vacate(level) >= 1, "an occupied camp should be forgotten in a peaceful world");
			helper.assertTrue(CampManager.find(level, center).isEmpty(), "the vacated camp should no longer be recorded");
			helper.assertTrue(CampManager.find(level, clearedCenter).map(CampState::cleared).orElse(false), "a cleared camp should stay cleared");

			// Two guards were removed by the game, one outlived the peace in an unloaded chunk.
			List<? extends Mob> guards = guardsOf(level, center);
			guards.get(0).discard();
			guards.get(1).discard();

			Optional<CampState> again = CampManager.activate(level, center, now + 20, config(3, 200));
			helper.assertTrue(again.isPresent() && again.get().guardsLeft() == 1, "the surviving guard should hold the camp alone, but the camp records "
					+ again.map(CampState::guardsLeft).orElse(-1));
			helper.assertTrue(guardsOf(level, center).stream().filter(Mob::isAlive).count() == 1, "nobody should be added to a surviving guard");

			guards.get(2).kill(level);
			helper.assertTrue(CampManager.find(level, center).orElseThrow().cleared(), "the camp should be cleared when the survivor dies");

			// With nobody left, a vacated camp gets a full set of guards again.
			CampManager.forget(level, center);
			helper.assertTrue(CampManager.activate(level, center, now + 40, config(3, 200)).map(CampState::guardsLeft).orElse(0) == 3,
					"an empty vacated camp should get its guards back");
		} finally {
			CampManager.forget(level, center);
			CampManager.forget(level, clearedCenter);
			// Camps of tests running at the same time were vacated too; put them back.
			List<CampState> restored = new ArrayList<>(CampManager.states(level));
			others.stream().filter(other -> CampManager.find(level, other.center()).isEmpty()).forEach(restored::add);
			level.setAttached(WildfrontAttachments.CAMPS, List.copyOf(restored));
			helper.killAllEntities();
		}

		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void serverWideGuardLimitPostponesACamp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ensureHostileWorld(level);
		GameTestSupport.buildFloor(helper);
		BlockPos center = helper.absolutePos(CENTER);
		int living = CampManager.states(level).stream().mapToInt(CampState::guardsLeft).sum();

		try {
			helper.assertTrue(CampManager.activate(level, center, level.getGameTime(), config(3, living + 2)).isEmpty(),
					"a camp should wait while its guards would exceed the server-wide limit");
			helper.assertTrue(guardsOf(level, center).isEmpty(), "no guards should be spawned for a postponed camp");
			helper.assertTrue(CampManager.find(level, center).isEmpty(), "a postponed camp should not be recorded, so that it is tried again");

			helper.assertTrue(CampManager.activate(level, center, level.getGameTime(), config(3, living + 3)).isPresent(),
					"the camp should be activated once its guards fit under the limit");
		} finally {
			CampManager.forget(level, center);
			helper.killAllEntities();
		}

		helper.succeed();
	}

	@GameTest
	public void campWithoutRoomForGuardsIsTriedAgainLater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ensureHostileWorld(level);
		// No floor was built: there is nothing to stand on at the camp's level.
		BlockPos center = helper.absolutePos(new BlockPos(3, 5, 3));

		try {
			helper.assertTrue(CampManager.activate(level, center, level.getGameTime(), config(3, 40)).isEmpty(), "a camp without room for guards should not be activated");
			helper.assertTrue(CampManager.find(level, center).isEmpty(), "and should not be recorded");
		} finally {
			CampManager.forget(level, center);
		}

		helper.succeed();
	}

	@GameTest
	public void leavesAboveACampAreClearedAndNothingElse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A camp of three by three blocks whose ground layer is at height 1 of the test.
		BlockPos low = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos high = helper.absolutePos(new BlockPos(4, 3, 4));
		BoundingBox camp = BoundingBox.fromCorners(low, high);

		helper.setBlock(new BlockPos(3, 1, 3), Blocks.OAK_LEAVES);      // in the ground layer: part of the camp's own level
		helper.setBlock(new BlockPos(3, 3, 3), Blocks.OAK_LEAVES);      // hanging into the camp
		helper.setBlock(new BlockPos(3, 6, 3), Blocks.SPRUCE_LEAVES);   // above it
		helper.setBlock(new BlockPos(3, 7, 3), Blocks.SNOW);            // lying on those leaves
		helper.setBlock(new BlockPos(1, 5, 5), Blocks.OAK_LEAVES);      // over the margin around the camp
		helper.setBlock(new BlockPos(0, 5, 3), Blocks.OAK_LEAVES);      // beyond the margin
		helper.setBlock(new BlockPos(4, 5, 4), Blocks.OAK_LOG);         // a branch
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.HAY_BLOCK);      // the camp itself

		int removed = CanopyClearing.clearAbove(level, camp, null);

		helper.assertTrue(removed == 4, "three leaf blocks and the snow on one of them should be removed, but " + removed + " blocks were");
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 3, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 6, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 7, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 5, 5));
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(3, 1, 3));
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(0, 5, 3));
		helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 5, 4));
		helper.assertBlockPresent(Blocks.HAY_BLOCK, new BlockPos(2, 2, 2));

		// Restricted to a chunk the camp does not reach into, nothing may be touched.
		helper.setBlock(new BlockPos(3, 3, 3), Blocks.OAK_LEAVES);
		ChunkPos elsewhere = new ChunkPos(ChunkPos.containing(low).x() + 3, ChunkPos.containing(low).z());
		helper.assertTrue(CanopyClearing.clearAbove(level, camp, elsewhere) == 0, "a chunk the camp does not reach into should be left alone");
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(3, 3, 3));
		helper.assertTrue(CanopyClearing.clearAbove(level, camp, ChunkPos.containing(helper.absolutePos(new BlockPos(3, 3, 3)))) == 1,
				"the chunk the leaves are in should be cleared");
		helper.succeed();
	}

	@GameTest
	public void campStateSurvivesSavingAndLoading(GameTestHelper helper) {
		CampState state = new CampState(new BlockPos(10, 64, -20), 2, 1234);
		JsonElement saved = CampState.CODEC.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();

		helper.assertTrue(state.equals(CampState.CODEC.parse(JsonOps.INSTANCE, saved).result().orElse(null)), "a camp state should survive saving and loading: " + saved);
		helper.assertTrue(new CampState(BlockPos.ZERO, 0, 0).cleared() && !state.cleared(), "a camp is cleared exactly when no guards are left");
		helper.assertTrue(state.withOneGuardLess().guardsLeft() == 1 && new CampState(BlockPos.ZERO, 0, 0).withOneGuardLess().guardsLeft() == 0,
				"guards are counted down to zero and no further");
		helper.succeed();
	}
}
