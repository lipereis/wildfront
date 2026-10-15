package dev.wildfront.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;

import dev.wildfront.camp.CampManager;
import dev.wildfront.camp.CampState;
import dev.wildfront.camp.CanopyClearing;
import dev.wildfront.config.HostileCampConfig;
import dev.wildfront.entity.GuardEntity;
import dev.wildfront.registry.WildfrontAttachments;
import dev.wildfront.registry.WildfrontEntities;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bandit camps on a real server: their data, their guards, and what happens when the guards die.
 */
public class BanditCampGameTest {
	private static final BlockPos CENTER = new BlockPos(3, 1, 3);

	private static HostileCampConfig config(int guards, int maxLiving) {
		return new HostileCampConfig(true, 48, guards, guards, maxLiving, 0.0);
	}

	private static HostileCampConfig withChief(int guards) {
		return new HostileCampConfig(true, 48, guards, guards, 200, 1.0);
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
			helper.assertTrue(guards.stream().allMatch(guard -> guard instanceof GuardEntity), "every guard of a camp should be a wildfront:guard, but they are "
					+ guards.stream().map(guard -> guard.getType().toShortString()).toList());
			helper.assertTrue(guards.stream().filter(guard -> guard.getMainHandItem().is(Items.IRON_SWORD)).count() == 1
					&& guards.stream().filter(guard -> guard.getMainHandItem().is(Items.BOW)).count() == 2, "three guards should be two archers and one swordsman, but they hold "
					+ guards.stream().map(guard -> guard.getMainHandItem().toString()).toList());

			for (Mob guard : guards) {
				helper.assertTrue(guard.isPersistenceRequired(), "a guard must not despawn");
				helper.assertTrue(guard.getHomeRadius() > 0 && guard.blockPosition().closerThan(center, 6), "a guard should start near the middle of its camp");
				helper.assertFalse(guard.getMainHandItem().isEmpty(), "a guard should be armed");
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
	public void chiefGuardsABarredStashUntilTheCampIsCleared(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ensureHostileWorld(level);
		GameTestSupport.buildFloor(helper);
		BlockPos center = helper.absolutePos(CENTER);
		BlockPos stash = helper.absolutePos(new BlockPos(6, 1, 3));
		helper.setBlock(new BlockPos(6, 1, 3), Blocks.BARREL);
		ServerPlayer player = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 7, 1, 7);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stash), Direction.UP, stash, false);

		try {
			CampState state = CampManager.activate(level, center, level.getGameTime(), withChief(2)).orElseThrow();
			helper.assertTrue(state.chief(), "with a chief chance of 1 and a barrel the camp should be led by a chief");
			helper.assertTrue(state.stash().equals(Optional.of(stash)), "the camp's barrel should be the stash, but the stash is " + state.stash());
			helper.assertTrue(state.guardsLeft() == 3, "two guards and the chief should be counted, but " + state.guardsLeft() + " are");

			List<GuardEntity> chiefs = guardsOf(level, center).stream().filter(guard -> guard instanceof GuardEntity g && g.isChief())
					.map(guard -> (GuardEntity) guard).toList();
			helper.assertTrue(chiefs.size() == 1, "exactly one guard should be the chief, but " + chiefs.size() + " are");
			GuardEntity chief = chiefs.get(0);
			helper.assertTrue(chief.getMaxHealth() == (float) GuardEntity.CHIEF_HEALTH && chief.getHealth() == chief.getMaxHealth(),
					"the chief should have " + GuardEntity.CHIEF_HEALTH + " health but has " + chief.getHealth() + " of " + chief.getMaxHealth());
			helper.assertTrue(chief.getMainHandItem().is(Items.DIAMOND_SWORD), "the chief should carry a diamond sword");
			helper.assertTrue(chief.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && chief.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
					"the chief should wear no armour items, which would hide its skin");
			helper.assertTrue(chief.getAttributeBaseValue(Attributes.ARMOR) == GuardEntity.CHIEF_ARMOR, "the chief should be armoured all the same");
			helper.assertTrue(chief.getAttributeValue(Attributes.SCALE) == GuardEntity.CHIEF_SCALE, "the chief should be bigger than its guards");
			helper.assertTrue(chief.getName().getString().equals("Camp Chief") || chief.getName().getString().equals("entity.wildfront.guard_chief"),
					"the chief should be named Camp Chief, but is " + chief.getName().getString());

			BarrelBlockEntity barrel = helper.getBlockEntity(new BlockPos(6, 1, 3), BarrelBlockEntity.class);
			helper.assertTrue(WildfrontLootTables.CHIEF_STASH.equals(barrel.getLootTable()), "the stash should hold the chief's loot, but its loot table is " + barrel.getLootTable());

			helper.assertTrue(CampManager.isBarred(level, stash), "the stash should be barred while the camp has guards");
			helper.assertTrue(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit) == InteractionResult.FAIL,
					"a survival player must not open the stash while the camp has guards");
			helper.assertFalse(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, stash, level.getBlockState(stash), barrel),
					"a survival player must not break the stash while the camp has guards");
			helper.assertFalse(CampManager.isBarred(level, center), "only the stash is barred");

			guardsOf(level, center).forEach(guard -> guard.kill(level));
			helper.assertTrue(CampManager.find(level, center).orElseThrow().cleared(), "the camp should be cleared when its guards and chief are dead");
			helper.assertFalse(CampManager.isBarred(level, stash), "the stash should be open once the camp is cleared");
			helper.assertTrue(UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
					"a survival player should be able to open the stash once the camp is cleared");
		} finally {
			GameTestSupport.remove(helper, player);
			CampManager.forget(level, center);
			helper.killAllEntities();
		}

		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void noChiefWithoutAStashOrWhenTheChanceSaysNo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ensureHostileWorld(level);
		GameTestSupport.buildFloor(helper);
		BlockPos center = helper.absolutePos(CENTER);

		try {
			// No barrel anywhere near: nothing for a chief to guard.
			CampState withoutStash = CampManager.activate(level, center, level.getGameTime(), withChief(2)).orElseThrow();
			helper.assertFalse(withoutStash.chief(), "a camp without a stash should not get a chief");
			helper.assertTrue(withoutStash.guardsLeft() == 2 && withoutStash.stash().isEmpty(), "a camp without a chief has only its guards");
			CampManager.forget(level, center);

			helper.setBlock(new BlockPos(6, 1, 3), Blocks.BARREL);
			CampState noChance = CampManager.activate(level, center, level.getGameTime(), config(2, 200)).orElseThrow();
			helper.assertFalse(noChance.chief(), "with a chief chance of 0 no camp should get a chief");
			helper.assertFalse(CampManager.isBarred(level, helper.absolutePos(new BlockPos(6, 1, 3))), "a camp without a chief bars nothing");
		} finally {
			CampManager.forget(level, center);
			helper.killAllEntities();
		}

		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void aGuardThatSpotsAPlayerAlertsItsCampOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ensureHostileWorld(level);
		GameTestSupport.buildFloor(helper);
		BlockPos center = helper.absolutePos(CENTER);
		ServerPlayer player = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 7, 1, 7);
		GuardEntity stranger = WildfrontEntities.GUARD.create(level, EntitySpawnReason.COMMAND);
		BlockPos strangerPos = helper.absolutePos(new BlockPos(0, 1, 7));
		stranger.snapTo(strangerPos.getX() + 0.5, strangerPos.getY(), strangerPos.getZ() + 0.5, 0.0F, 0.0F);
		stranger.setNoAi(true);
		level.addFreshEntity(stranger);

		try {
			CampManager.activate(level, center, level.getGameTime(), config(3, 200)).orElseThrow();
			List<GuardEntity> guards = guardsOf(level, center).stream().map(guard -> (GuardEntity) guard).toList();
			guards.forEach(guard -> guard.setTarget(null));

			guards.get(0).setTarget(player);

			for (GuardEntity guard : guards) {
				helper.assertTrue(guard.getTarget() == player, "every guard of the camp should go for the player once one spotted them, but one targets " + guard.getTarget());
			}

			helper.assertTrue(stranger.getTarget() == null, "a guard of no camp should not hear another camp's alarm");
		} finally {
			GameTestSupport.remove(helper, player);
			CampManager.forget(level, center);
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
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.HAY_BLOCK);      // the camp itself

		int removed = CanopyClearing.clearAbove(level, camp);

		helper.assertTrue(removed == 4, "three leaf blocks and the snow on one of them should be removed, but " + removed + " blocks were");
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 3, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 6, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 7, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 5, 5));
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(3, 1, 3));
		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(0, 5, 3));
		helper.assertBlockPresent(Blocks.HAY_BLOCK, new BlockPos(2, 2, 2));
		helper.assertTrue(CanopyClearing.clearAbove(level, camp) == 0, "clearing a second time should find nothing left to do");
		helper.succeed();
	}

	@GameTest
	public void treesLeftBareByTheClearingAreRemoved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A camp of three by three blocks at 2..4. Its ground layer, at height 1, is packed mud
		// except for the corner at 4, 4, where the camp keeps the terrain.
		BoundingBox camp = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(2, 1, 2)), helper.absolutePos(new BlockPos(4, 3, 4)));

		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 1, z), x == 4 && z == 4 ? Blocks.DIRT : Blocks.PACKED_MUD);
			}
		}

		// The palisade: logs on the camp's own ground.
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.SPRUCE_LOG);
		helper.setBlock(new BlockPos(2, 3, 2), Blocks.SPRUCE_LOG);

		// A tree rooted in the corner of the camp, with the outer fringe of its crown.
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(new BlockPos(4, y, 4), Blocks.BIRCH_LOG);
		}

		helper.setBlock(new BlockPos(4, 6, 4), Blocks.BIRCH_LEAVES);

		for (int z = 2; z <= 6; z++) {
			helper.setBlock(new BlockPos(6, 5, z), Blocks.BIRCH_LEAVES);
			helper.setBlock(new BlockPos(6, 4, z), Blocks.BIRCH_LEAVES);
		}

		// A tree beside the camp that keeps the outer half of its crown.
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(new BlockPos(1, y, 3), Blocks.OAK_LOG);
		}

		for (int z = 1; z <= 5; z++) {
			helper.setBlock(new BlockPos(0, 4, z), Blocks.OAK_LEAVES);
			helper.setBlock(new BlockPos(0, 3, z), Blocks.OAK_LEAVES);
		}

		// A tree beside the camp whose crown was all over the camp, bar two leaves.
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(new BlockPos(3, y, 1), Blocks.OAK_LOG);
		}

		helper.setBlock(new BlockPos(3, 5, 1), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(3, 4, 0), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(4, 4, 0), Blocks.OAK_LEAVES);

		CanopyClearing.clearAbove(level, camp);

		helper.assertBlockPresent(Blocks.SPRUCE_LOG, new BlockPos(2, 2, 2));
		helper.assertBlockPresent(Blocks.SPRUCE_LOG, new BlockPos(2, 3, 2));

		for (int y = 2; y <= 6; y++) {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, y, 4));
		}

		for (int z = 2; z <= 6; z++) {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 5, z));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 4, z));
		}

		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(4, 1, 4));

		for (int y = 1; y <= 4; y++) {
			helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, y, 3));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, y, 1));
		}

		helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(0, 4, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 4, 0));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 4, 0));
		helper.succeed();
	}

	@GameTest
	public void campStateSurvivesSavingAndLoading(GameTestHelper helper) {
		CampState state = new CampState(new BlockPos(10, 64, -20), 2, 1234);
		JsonElement saved = CampState.CODEC.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();

		helper.assertTrue(state.equals(CampState.CODEC.parse(JsonOps.INSTANCE, saved).result().orElse(null)), "a camp state should survive saving and loading: " + saved);

		CampState chief = new CampState(new BlockPos(10, 64, -20), 3, 1234, true, Optional.of(new BlockPos(12, 64, -20)));
		JsonElement savedChief = CampState.CODEC.encodeStart(JsonOps.INSTANCE, chief).result().orElseThrow();
		helper.assertTrue(chief.equals(CampState.CODEC.parse(JsonOps.INSTANCE, savedChief).result().orElse(null)), "a chief's camp should survive saving and loading: " + savedChief);

		// As saved by 0.4.0, before camps could have a chief.
		JsonObject old = new JsonObject();
		old.add("center", BlockPos.CODEC.encodeStart(JsonOps.INSTANCE, new BlockPos(10, 64, -20)).result().orElseThrow());
		old.addProperty("guards_left", 2);
		old.addProperty("activated_at", 1234L);
		helper.assertTrue(state.equals(CampState.CODEC.parse(JsonOps.INSTANCE, old).result().orElse(null)), "a camp saved by 0.4.0 should load as a camp without a chief: " + old);
		helper.assertTrue(new CampState(BlockPos.ZERO, 0, 0).cleared() && !state.cleared(), "a camp is cleared exactly when no guards are left");
		helper.assertTrue(state.withOneGuardLess().guardsLeft() == 1 && new CampState(BlockPos.ZERO, 0, 0).withOneGuardLess().guardsLeft() == 0,
				"guards are counted down to zero and no further");
		helper.succeed();
	}
}
