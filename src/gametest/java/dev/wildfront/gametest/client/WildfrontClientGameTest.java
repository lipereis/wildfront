package dev.wildfront.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.wildfront.camp.CampManager;
import dev.wildfront.camp.CampState;
import dev.wildfront.config.ConfigManager;
import dev.wildfront.discovery.ActiveCache;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.entity.GuardEntity;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;
import dev.wildfront.worldgen.HeightSpread;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Plays through the mod in a real client with a normally generated world: surveys where camps
 * generate, visits a naturally generated camp of every layout, triggers the Mysterious Discovery event by command and lets it trigger
 * on its own. Screenshots of each step are written to {@code build/run/clientGameTest/screenshots}
 * for a human to look at.
 *
 * <p>Run with {@code gradlew runClientGameTest}. It opens a game window, so it is not part of
 * {@code build}.
 */
public class WildfrontClientGameTest implements FabricClientGameTest {
	private static final String SEED = "wildfront";
	/**
	 * The survey stops once every layout was seen and this many camps were found. Enough for the
	 * rarest layout (2 in 10) to be missed by chance less than once in a thousand runs.
	 */
	private static final int SURVEY_ENOUGH_CAMPS = 40;
	private static final int SURVEY_MAX_CANDIDATES = 1200;
	private static final int WOODED_CAMPS_TO_VISIT = 6;
	private static final int BANDIT_SURVEY_ENOUGH_CAMPS = 10;

	/**
	 * The blocks a camp's ground layer is made of.
	 */
	private static final Set<Block> CAMP_GROUND = Set.of(Blocks.PACKED_MUD, Blocks.GRAVEL, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE);

	/**
	 * A block that only this layout contains, by the layout's short name. The cart layout has none
	 * and is recognised by its raised chest instead.
	 */
	private static final Map<String, Block> SIGNATURE_BLOCKS = Map.of(
			"camp", Blocks.CRAFTING_TABLE,
			"tent", Blocks.CAULDRON,
			"ruin", Blocks.FURNACE);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(settings -> {
					settings.setSeed(SEED);
					settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					settings.setAllowCommands(true);
				})
				.create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			String player = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getGameProfile().name());
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");

			guardPortrait(context, world, player);
			List<BlockPos> visited = naturallyGeneratedCamps(context, world, player);
			campsAmongTrees(world, player);
			occupiedBanditCamp(context, world, player);
			banditCampsUnderTrees(context, world, player);
			layoutGallery(context, world, player, visited);
			forcedEvent(context, world, player);
			automaticEvent(context, world, player);
		}
	}

	/**
	 * Summons a guard on a platform in the sky and takes its picture from the front and the back,
	 * which is the only way to see whether the skin sits right on the model.
	 */
	private static void guardPortrait(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		BlockPos at = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
		int x = at.getX();
		int y = 180;
		int z = at.getZ();

		server.runCommand("gamemode spectator " + player);
		server.runCommand("fill " + (x - 3) + " " + y + " " + (z - 5) + " " + (x + 3) + " " + y + " " + (z + 5) + " minecraft:smooth_stone");
		server.runCommand("summon wildfront:guard " + (x + 0.5) + " " + (y + 1) + " " + (z + 0.5));
		server.runCommand("data merge entity @e[type=wildfront:guard,limit=1] {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");

		try {
			List<ItemStack> held = server.computeOnServer(s -> s.overworld().getEntities(EntityTypeTest.forClass(GuardEntity.class), guard -> true).stream()
					.map(guard -> guard.getMainHandItem().copy()).toList());
			check(held.size() == 1, "the summon command should create exactly one guard, but there are " + held.size());
			check(held.get(0).is(Items.IRON_SWORD), "a summoned guard should carry an iron sword but holds " + held.get(0));

			server.runCommand("tp " + player + " " + (x + 0.5) + " " + (y + 1.2) + " " + (z - 2.3) + " 0 5");
			world.getConnection().waitForChunksRender();
			// Long enough for the chat lines of the commands above to fade.
			takeWorldScreenshot(context, "0_guard_front");

			server.runCommand("tp " + player + " " + (x + 0.5) + " " + (y + 1.2) + " " + (z + 3.3) + " 180 5");
			takeWorldScreenshot(context, "0_guard_back");
		} finally {
			server.runCommand("kill @e[type=wildfront:guard]");
			server.runCommand("kill @e[type=minecraft:item]");
			server.runCommand("fill " + (x - 3) + " " + y + " " + (z - 5) + " " + (x + 3) + " " + y + " " + (z + 5) + " minecraft:air");
		}
	}

	/**
	 * Surveys where world generation will put camps, checks that every layout occurs, then visits
	 * the nearest camp of each layout and checks what actually generated there.
	 *
	 * @return the campfire positions of the visited camps
	 */
	private static List<BlockPos> naturallyGeneratedCamps(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		CampSurvey.Result survey = server.computeOnServer(s -> CampSurvey.run(s.overworld(),
				s.getPlayerList().getPlayers().get(0).blockPosition(), WildfrontStructures.ABANDONED_CAMP, WildfrontStructures.ABANDONED_CAMP_LAYOUTS,
				SURVEY_ENOUGH_CAMPS, SURVEY_MAX_CANDIDATES));
		System.out.println("[WildfrontClientGameTest] survey: " + survey.camps().size() + " camps at " + survey.candidates()
				+ " candidate spots (" + Math.round(100.0 * survey.camps().size() / survey.candidates()) + "%), by layout: " + survey.countByLayout());

		check(!survey.camps().isEmpty(), "world generation should place abandoned camps, but none of " + survey.candidates() + " candidate spots has one");

		for (CampSurvey.Camp camp : survey.camps()) {
			check(WildfrontStructures.ABANDONED_CAMP_LAYOUTS.contains(camp.layout()), "unexpected camp layout " + camp.layout() + " at " + camp.start().toShortString());
		}

		for (Identifier layout : WildfrontStructures.ABANDONED_CAMP_LAYOUTS) {
			check(survey.countByLayout().get(layout) > 0, "world generation never chose layout " + layout + " among " + survey.camps().size()
					+ " camps: " + survey.countByLayout());
		}

		List<BlockPos> visited = new ArrayList<>();

		for (Identifier layout : WildfrontStructures.ABANDONED_CAMP_LAYOUTS) {
			BlockPos located = survey.firstWithLayout(layout).start();
			String name = layout.getPath().substring(layout.getPath().lastIndexOf('/') + 1);

			server.runCommand("gamemode spectator " + player);
			server.runCommand("tp " + player + " " + located.getX() + " 200 " + located.getZ());
			world.getConnection().waitForChunksRender();

			BlockPos campfire = server.computeOnServer(s -> findCampfire(s.overworld(), located));
			check(campfire != null, "the " + name + " camp at " + located.toShortString() + " should have generated its campfire");

			boolean hasLootChest = server.computeOnServer(s -> BlockPos.betweenClosedStream(campfire.offset(-6, -1, -6), campfire.offset(6, 2, 6))
					.anyMatch(pos -> s.overworld().getBlockEntity(pos) instanceof ChestBlockEntity chest
							&& WildfrontLootTables.ABANDONED_CAMP.equals(chest.getLootTable())));
			check(hasLootChest, "the generated " + name + " camp should contain its loot chest");

			// What was built must be the layout world generation announced. Three layouts have a
			// block no other has; the cart is the only one whose chest stands above the ground.
			int chestHeight = server.computeOnServer(s -> BlockPos.betweenClosedStream(campfire.offset(-6, -1, -6), campfire.offset(6, 2, 6))
					.filter(pos -> s.overworld().getBlockEntity(pos) instanceof ChestBlockEntity).mapToInt(pos -> pos.getY() - campfire.getY()).max().orElse(-1));

			if (SIGNATURE_BLOCKS.containsKey(name)) {
				boolean hasSignature = server.computeOnServer(s -> BlockPos.betweenClosedStream(campfire.offset(-6, -1, -6), campfire.offset(6, 3, 6))
						.anyMatch(pos -> s.overworld().getBlockState(pos).is(SIGNATURE_BLOCKS.get(name))));
				check(hasSignature, "the camp world generation announced as " + name + " should contain " + SIGNATURE_BLOCKS.get(name));
				check(chestHeight == 0, "the chest of the " + name + " camp should stand on the ground, but is " + chestHeight + " block(s) above the campfire");
			} else {
				check(chestHeight == 2, "the chest of the " + name + " camp should ride on the cart, 2 blocks above the campfire, but is " + chestHeight + " above");
			}

			int offGround = server.computeOnServer(s -> heightAboveGround(s.overworld(), campfire));
			check(Math.abs(offGround) <= 1, "the " + name + " camp should stand at ground level, but its campfire is " + offGround + " block(s) off");
			int spread = server.computeOnServer(s -> terrainSpread(s.overworld(), located));
			check(spread <= 3, "camps should only generate on roughly level ground, but the terrain around the " + name + " camp varies by " + spread + " blocks");
			System.out.println("[WildfrontClientGameTest] natural " + name + " camp at " + campfire.toShortString() + ", terrain varies by " + spread
					+ " block(s), campfire " + offGround + " block(s) off ground level");

			server.runCommand("tp " + player + " " + (campfire.getX() + 8.5) + " " + (campfire.getY() + 6) + " " + (campfire.getZ() + 8.5) + " 135 35");
			world.getConnection().waitForChunksRender();
			takeWorldScreenshot(context, "1_natural_" + name);
			visited.add(campfire);
		}

		return visited;
	}

	/**
	 * Visits camps in forest and taiga biomes and checks that no tree took root on a camp's own
	 * ground. Trees beside a camp, and their leaves over it, are fine; a trunk standing in the
	 * middle of it is not.
	 */
	private static void campsAmongTrees(TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		CampSurvey.Result survey = server.computeOnServer(s -> CampSurvey.run(s.overworld(),
				s.getPlayerList().getPlayers().get(0).blockPosition(), WildfrontStructures.ABANDONED_CAMP, WildfrontStructures.ABANDONED_CAMP_LAYOUTS,
				SURVEY_ENOUGH_CAMPS, SURVEY_MAX_CANDIDATES));
		List<CampSurvey.Camp> wooded = survey.camps().stream().filter(CampSurvey.Camp::wooded).limit(WOODED_CAMPS_TO_VISIT).toList();
		check(!wooded.isEmpty(), "expected at least one camp in a forest or taiga among " + survey.camps().size() + " camps");
		int rooted = 0;

		for (CampSurvey.Camp camp : wooded) {
			BlockPos located = camp.start();
			server.runCommand("gamemode spectator " + player);
			server.runCommand("tp " + player + " " + located.getX() + " 200 " + located.getZ());
			world.getConnection().waitForChunksRender();

			BlockPos campfire = server.computeOnServer(s -> findCampfire(s.overworld(), located));
			check(campfire != null, "the camp at " + located.toShortString() + " should have generated its campfire");
			int trunks = server.computeOnServer(s -> trunksRootedOnCampGround(s.overworld(), campfire));
			System.out.println("[WildfrontClientGameTest] wooded camp at " + campfire.toShortString() + ": " + trunks + " tree trunk(s) rooted on its ground");
			rooted += trunks;
		}

		check(rooted == 0, rooted + " tree trunk(s) took root on the ground of " + wooded.size() + " camps in wooded biomes");
	}

	/**
	 * Counts upright logs standing directly on a block of a camp's ground layer. The logs a camp
	 * itself contains lie on their side, so an upright one is a tree.
	 */
	private static int trunksRootedOnCampGround(ServerLevel level, BlockPos campfire) {
		int trunks = 0;

		for (BlockPos ground : BlockPos.betweenClosed(campfire.offset(-6, -1, -6), campfire.offset(6, -1, 6))) {
			BlockState above = level.getBlockState(ground.above());

			if (CAMP_GROUND.contains(level.getBlockState(ground).getBlock()) && above.is(BlockTags.LOGS)
					&& above.hasProperty(RotatedPillarBlock.AXIS) && above.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) {
				trunks++;
			}
		}

		return trunks;
	}

	/**
	 * Finds a naturally generated bandit camp, walks a survival player up to it and checks the
	 * whole life of the camp: guards appear once, are counted as they die, and never come back.
	 */
	private static void occupiedBanditCamp(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		CampSurvey.Result survey = server.computeOnServer(s -> CampSurvey.run(s.overworld(), s.getPlayerList().getPlayers().get(0).blockPosition(),
				WildfrontStructures.BANDIT_CAMP, WildfrontStructures.BANDIT_CAMP_LAYOUTS, BANDIT_SURVEY_ENOUGH_CAMPS, SURVEY_MAX_CANDIDATES));
		System.out.println("[WildfrontClientGameTest] bandit camp survey: " + survey.camps().size() + " camps at " + survey.candidates()
				+ " candidate spots (" + Math.round(100.0 * survey.camps().size() / survey.candidates()) + "%)");
		check(!survey.camps().isEmpty(), "world generation should place bandit camps, but none of " + survey.candidates() + " candidate spots has one");

		for (CampSurvey.Camp camp : survey.camps()) {
			check((long) camp.start().getX() * camp.start().getX() + (long) camp.start().getZ() * camp.start().getZ() >= 390L * 390L,
					"bandit camps should keep away from the world origin, but one is at " + camp.start().toShortString());
		}

		// Prefer a camp in open country: under a forest canopy the picture shows only leaves.
		CampSurvey.Camp visited = survey.camps().stream().filter(camp -> !camp.wooded()).findFirst().orElse(survey.camps().get(0));
		System.out.println("[WildfrontClientGameTest] visiting the bandit camp at " + visited.start().toShortString() + (visited.wooded() ? " (wooded)" : " (open country)"));
		BlockPos located = visited.start();
		Path config =FabricLoader.getInstance().getConfigDir().resolve(ConfigManager.FILE_NAME);
		String original;

		try {
			original = Files.readString(config);
			// Check players every second; no events, so nothing else happens around the camp.
			Files.writeString(config, """
					{ "checkIntervalSeconds": 1, "events": { "mysterious_discovery": { "enabled": false } } }
					""");
		} catch (IOException e) {
			throw new AssertionError("could not write the test config", e);
		}

		try {
			server.runCommand("wildfront reload");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode spectator " + player);
			// The player hangs in the air whenever it is in survival mode below; on a slow machine
			// it reaches the ground before it is a spectator again, and must survive that.
			server.runCommand("effect give " + player + " minecraft:resistance 600 4 true");
			server.runCommand("tp " + player + " " + located.getX() + " 200 " + located.getZ());
			world.getConnection().waitForChunksRender();

			// A spectator is not a player the mod reacts to: the camp must still be asleep.
			context.waitTicks(40);
			check(server.computeOnServer(s -> CampManager.states(s.overworld()).isEmpty()), "a spectator flying over a camp should not wake it");

			BlockPos fire = server.computeOnServer(s -> findBlock(s.overworld(), located, Blocks.CAMPFIRE));
			check(fire != null, "the bandit camp at " + located.toShortString() + " should have generated its fire");
			check(server.computeOnServer(s -> s.overworld().getBlockState(fire).getValue(CampfireBlock.LIT)), "the fire of a bandit camp should burn");
			check(server.computeOnServer(s -> findBlock(s.overworld(), located, Blocks.TARGET)) != null, "the bandit camp should contain its practice target");
			int offGround = server.computeOnServer(s -> heightAboveGround(s.overworld(), fire));
			check(Math.abs(offGround) <= 1, "the bandit camp should stand at ground level, but its fire is " + offGround + " block(s) off");

			// Walk up as a survival player; within a second or two the guards must be there.
			server.runCommand("tp " + player + " " + (fire.getX() + 20.5) + " " + (fire.getY() + 30) + " " + (fire.getZ() + 0.5));
			server.runCommand("gamemode survival " + player);
			server.waitFor(s -> !CampManager.states(s.overworld()).isEmpty(), 200);
			server.runCommand("gamemode spectator " + player);

			CampState occupied = server.computeOnServer(s -> CampManager.states(s.overworld()).get(0));
			int alive = server.computeOnServer(s -> countGuards(s.overworld()));
			System.out.println("[WildfrontClientGameTest] bandit camp at " + occupied.center().toShortString() + " occupied by " + occupied.guardsLeft()
					+ " guard(s), " + alive + " alive, fire " + offGround + " block(s) off ground level");
			check(occupied.center().closerThan(fire, 3), "the camp's recorded middle " + occupied.center().toShortString() + " should be at its fire " + fire.toShortString());
			check(occupied.guardsLeft() >= 2 && occupied.guardsLeft() <= 3, "a small camp should have 2 or 3 guards but has " + occupied.guardsLeft());
			check(alive == occupied.guardsLeft(), "the camp records " + occupied.guardsLeft() + " guard(s) but " + alive + " are alive");

			server.runCommand("tp " + player + " " + (fire.getX() + 9.5) + " " + (fire.getY() + 7) + " " + (fire.getZ() + 9.5) + " 135 35");
			world.getConnection().waitForChunksRender();
			takeWorldScreenshot(context, "1_bandit_camp");

			// Ten more checks: nobody new may appear.
			check(server.computeOnServer(s -> countGuards(s.overworld())) == occupied.guardsLeft(), "no further guards should appear at an occupied camp");

			// Peaceful: the game removes the guards without their dying, and the camp must not
			// stay recorded as occupied.
			String above = (fire.getX() + 20.5) + " " + (fire.getY() + 30) + " " + (fire.getZ() + 0.5);
			server.runCommand("difficulty peaceful");
			server.runCommand("tp " + player + " " + above);
			server.runCommand("gamemode survival " + player);
			server.waitFor(s -> CampManager.states(s.overworld()).isEmpty(), 200);
			server.runCommand("gamemode spectator " + player);
			server.waitFor(s -> countGuards(s.overworld()) == 0, 200);
			System.out.println("[WildfrontClientGameTest] bandit camp stands empty in a peaceful world");

			// Back on normal the camp is occupied again.
			server.runCommand("difficulty normal");
			server.runCommand("tp " + player + " " + above);
			server.runCommand("gamemode survival " + player);
			server.waitFor(s -> !CampManager.states(s.overworld()).isEmpty(), 200);
			server.runCommand("gamemode spectator " + player);
			int back = server.computeOnServer(s -> CampManager.states(s.overworld()).get(0).guardsLeft());
			check(back >= 2 && back <= 3 && server.computeOnServer(s -> countGuards(s.overworld())) == back,
					"after peaceful the camp should be occupied by 2 or 3 guards again, but it records " + back);
			System.out.println("[WildfrontClientGameTest] bandit camp occupied again by " + back + " guard(s) after peaceful");

			server.runCommand("kill @e[tag=" + CampManager.GUARD_TAG + "]");
			server.waitFor(s -> CampManager.states(s.overworld()).get(0).cleared(), 100);

			// Come back as a survival player: a cleared camp stays empty. Low enough to survive
			// the fall, as these five seconds are long enough to reach the ground.
			server.runCommand("tp " + player + " " + (fire.getX() + 9.5) + " " + (fire.getY() + 4) + " " + (fire.getZ() + 9.5));
			server.runCommand("gamemode survival " + player);
			context.waitTicks(100);
			server.runCommand("gamemode spectator " + player);
			check(server.computeOnServer(s -> countGuards(s.overworld())) == 0, "no guards should come back to a cleared camp");
			check(server.computeOnServer(s -> CampManager.states(s.overworld()).size()) == 1, "the camp should be recorded exactly once");
		} finally {
			try {
				Files.writeString(config, original);
			} catch (IOException e) {
				System.out.println("[WildfrontClientGameTest] could not restore " + config + ": " + e);
			}

			server.runCommand("kill @e[tag=" + CampManager.GUARD_TAG + "]");
			server.runCommand("effect clear " + player + " minecraft:resistance");
			server.runCommand("wildfront reload");
		}
	}

	/**
	 * Visits bandit camps in forest and taiga biomes and checks that no leaves hang over them: an
	 * occupied camp under a closed canopy cannot be seen from outside, and hardly walked through.
	 */
	private static void banditCampsUnderTrees(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		CampSurvey.Result survey = server.computeOnServer(s -> CampSurvey.run(s.overworld(), s.getPlayerList().getPlayers().get(0).blockPosition(),
				WildfrontStructures.BANDIT_CAMP, WildfrontStructures.BANDIT_CAMP_LAYOUTS, BANDIT_SURVEY_ENOUGH_CAMPS, SURVEY_MAX_CANDIDATES));
		List<CampSurvey.Camp> wooded = survey.camps().stream().filter(CampSurvey.Camp::wooded).limit(WOODED_CAMPS_TO_VISIT).toList();
		check(!wooded.isEmpty(), "expected at least one bandit camp in a forest or taiga among " + survey.camps().size() + " camps");
		int overhanging = 0;
		int standing = 0;

		for (CampSurvey.Camp camp : wooded) {
			BlockPos located = camp.start();
			server.runCommand("gamemode spectator " + player);
			server.runCommand("tp " + player + " " + located.getX() + " 200 " + located.getZ());
			waitForTerrain(context, world, located);

			BlockPos fire = server.computeOnServer(s -> findBlock(s.overworld(), located, Blocks.CAMPFIRE));
			check(fire != null, "the bandit camp at " + located.toShortString() + " should have generated its fire");
			int leaves = server.computeOnServer(s -> leavesOver(s.overworld(), fire));
			int trunks = server.computeOnServer(s -> treeLogsIn(s.overworld(), fire));
			System.out.println("[WildfrontClientGameTest] wooded bandit camp at " + fire.toShortString() + ": " + leaves + " leaf block(s) over it, "
					+ trunks + " log(s) of trees standing in it");
			overhanging += leaves;
			standing += trunks;
		}

		check(overhanging == 0, overhanging + " leaf block(s) hang over " + wooded.size() + " bandit camps in wooded biomes");
		check(standing == 0, standing + " log(s) of bare trees stand in " + wooded.size() + " bandit camps in wooded biomes");
	}

	/**
	 * Counts upright logs within the nine by nine blocks of a bandit camp that do not stand on the
	 * camp's own ground: the palisade does, a tree that took root in a corner of the camp does not.
	 */
	private static int treeLogsIn(ServerLevel level, BlockPos fire) {
		int logs = 0;

		for (BlockPos column : BlockPos.betweenClosed(fire.offset(-4, -1, -4), fire.offset(4, -1, 4))) {
			if (CAMP_GROUND.contains(level.getBlockState(column).getBlock())) {
				continue;
			}

			for (int dy = -1; dy <= 21; dy++) {
				BlockState state = level.getBlockState(column.above(dy));

				if (state.is(BlockTags.LOGS) && state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) {
					logs++;
				}
			}
		}

		return logs;
	}

	/**
	 * Counts leaves above the nine by nine blocks of a bandit camp, whose fire is in its middle.
	 */
	private static int leavesOver(ServerLevel level, BlockPos fire) {
		int leaves = 0;

		for (BlockPos pos : BlockPos.betweenClosed(fire.offset(-4, 0, -4), fire.offset(4, 20, 4))) {
			if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
				leaves++;
			}
		}

		return leaves;
	}

	/**
	 * Waits until the server has loaded the terrain around a position the player was just sent to,
	 * and the client has drawn it. Asking the client alone is not enough: right after a long
	 * teleport it has nothing left to draw and says it is done.
	 */
	private static void waitForTerrain(ClientGameTestContext context, TestSingleplayerContext world, BlockPos around) {
		world.getServer().waitFor(s -> {
			for (int dx = -64; dx <= 64; dx += 32) {
				for (int dz = -64; dz <= 64; dz += 32) {
					if (!s.overworld().isLoaded(around.offset(dx, 0, dz))) {
						return false;
					}
				}
			}

			return true;
		}, 400);
		world.getConnection().waitForChunksRender();
		context.waitTicks(10);
	}

	private static int countGuards(ServerLevel level) {
		return level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob.isAlive() && mob.entityTags().contains(CampManager.GUARD_TAG)).size();
	}

	private static BlockPos findBlock(ServerLevel level, BlockPos near, Block block) {
		for (BlockPos pos : BlockPos.betweenClosed(near.getX() - 16, level.getMinY(), near.getZ() - 16, near.getX() + 16, level.getMaxY(), near.getZ() + 16)) {
			if (level.getBlockState(pos).is(block)) {
				return pos.immutable();
			}
		}

		return null;
	}

	/**
	 * Builds every camp layout on a dry, level, open patch of the terrain and takes a picture of
	 * each, so a change to a layout can be looked at without hunting for it in the world.
	 */
	private static void layoutGallery(ClientGameTestContext context, TestSingleplayerContext world, String player, List<BlockPos> around) {
		TestServerContext server = world.getServer();
		List<BlockPos> sites = List.of();

		// Open, level grass is not guaranteed anywhere; look around each camp that was visited.
		for (BlockPos center : around) {
			server.runCommand("tp " + player + " " + center.getX() + " " + (center.getY() + 20) + " " + center.getZ());
			waitForTerrain(context, world, center);
			sites = server.computeOnServer(s -> findGallerySites(s.overworld(), center, WildfrontStructures.ABANDONED_CAMP_LAYOUTS.size()));

			if (sites.size() == WildfrontStructures.ABANDONED_CAMP_LAYOUTS.size()) {
				break;
			}
		}

		if (sites.size() != WildfrontStructures.ABANDONED_CAMP_LAYOUTS.size()) {
			System.out.println("[WildfrontClientGameTest] layout gallery skipped: no open, level ground for every layout near the visited camps");
			return;
		}

		for (int i = 0; i < sites.size(); i++) {
			Identifier layout = WildfrontStructures.ABANDONED_CAMP_LAYOUTS.get(i);
			BlockPos site = sites.get(i);
			String name = layout.getPath().substring(layout.getPath().lastIndexOf('/') + 1);

			// One below the surface, so the layout's ground layer replaces the top block.
			server.runCommand("place template " + layout + " " + site.getX() + " " + (site.getY() - 1) + " " + site.getZ());
			check(server.computeOnServer(s -> BlockPos.betweenClosedStream(site.below(), site.offset(6, 2, 6))
					.anyMatch(pos -> s.overworld().getBlockState(pos).is(Blocks.CAMPFIRE))), "layout " + layout + " should have been built at " + site.toShortString());

			// From the south-east, which is the side the tent and the ruin are open to.
			server.runCommand("gamemode spectator " + player);
			server.runCommand("tp " + player + " " + (site.getX() + 9.5) + " " + (site.getY() + 6) + " " + (site.getZ() + 9.5) + " 135 38");
			world.getConnection().waitForChunksRender();
			takeWorldScreenshot(context, "1_layout_" + name);
		}
	}

	/**
	 * Finds north-west corners of 7x7 patches near {@code center} that are level, dry and not under
	 * trees, at least 12 blocks apart. Only looks at loaded chunks.
	 */
	private static List<BlockPos> findGallerySites(ServerLevel level, BlockPos center, int count) {
		List<BlockPos> sites = new ArrayList<>();

		for (int ring = 1; ring <= 7 && sites.size() < count; ring++) {
			for (int dx = -ring; dx <= ring && sites.size() < count; dx++) {
				for (int dz = -ring; dz <= ring && sites.size() < count; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					int x = center.getX() + dx * 12;
					int z = center.getZ() + dz * 12;

					if (!level.isLoaded(new BlockPos(x, center.getY(), z)) || !level.isLoaded(new BlockPos(x + 6, center.getY(), z + 6))) {
						continue;
					}

					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					boolean suitable = true;

					for (int ox = 0; ox <= 6 && suitable; ox += 3) {
						for (int oz = 0; oz <= 6 && suitable; oz += 3) {
							BlockPos ground = new BlockPos(x + ox, y - 1, z + oz);
							suitable = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + ox, z + oz) == y
									&& level.getHeight(Heightmap.Types.WORLD_SURFACE, x + ox, z + oz) <= y + 1
									&& level.getBlockState(ground).is(Blocks.GRASS_BLOCK)
									&& level.getFluidState(ground.above()).isEmpty();
						}
					}

					if (suitable) {
						sites.add(new BlockPos(x, y, z));
					}
				}
			}
		}

		return sites;
	}

	private static void forcedEvent(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		// The event only uses loaded chunks; right after a long teleport there are none yet.
		waitForTerrain(context, world, server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition()));
		int before = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).size());

		server.runCommand("wildfront event trigger mysterious_discovery " + player);
		context.waitTicks(5);
		context.takeScreenshot("2_event_message");

		List<ActiveCache> caches = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()));
		check(caches.size() == before + 1, "the trigger command should place exactly one cache, but active caches went from " + before + " to " + caches.size());
		BlockPos cache = caches.get(caches.size() - 1).pos();
		check(server.computeOnServer(s -> s.overworld().getBlockState(cache).is(Blocks.BARREL)), "the cache should be a barrel");

		System.out.println("[WildfrontClientGameTest] forced cache at " + cache.toShortString());
		lookAt(context, world, player, cache);
		// Particles appear on the cache's 2-second cycle; wait for a fresh burst before the picture.
		context.waitTicks(45);
		takeWorldScreenshot(context, "3_forgotten_cache");
	}

	private static void automaticEvent(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		Path config = FabricLoader.getInstance().getConfigDir().resolve(ConfigManager.FILE_NAME);

		try {
			Files.writeString(config, """
					{
						"checkIntervalSeconds": 1,
						"globalCooldownSeconds": 0,
						"events": {
							"mysterious_discovery": { "cooldownSeconds": 600, "triggerChance": 1, "minDistanceFromSpawn": 0 }
						}
					}
					""");
			server.runCommand("wildfront reload");
			server.runCommand("wildfront event reset " + player);
			server.runCommand("gamemode survival " + player);
			int before = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).size());

			// One check per second and a certain trigger: the event must fire within a few seconds.
			server.waitFor(s -> DiscoveryManager.activeCaches(s.overworld()).size() > before, 200);
			context.waitTicks(10);
			context.takeScreenshot("4_automatic_event");

			// Ten more checks during the 600-second cooldown must not trigger anything.
			context.waitTicks(200);
			int after = server.computeOnServer(s -> DiscoveryManager.activeCaches(s.overworld()).size());
			check(after == before + 1, "the cooldown should allow exactly one automatic event, but active caches went from " + before + " to " + after);
		} catch (IOException e) {
			throw new AssertionError("could not write the test config", e);
		} finally {
			try {
				Files.deleteIfExists(config);
			} catch (IOException e) {
				System.out.println("[WildfrontClientGameTest] could not delete " + config + ": " + e);
			}

			server.runCommand("wildfront reload");
		}
	}

	/**
	 * Difference between the highest and lowest terrain height the generator reports around the
	 * camp's start, sampled the way the camp's structure type samples it.
	 */
	static int terrainSpread(ServerLevel level, BlockPos located) {
		int[][] offsets = HeightSpread.sampleOffsets(7);
		int[] heights = new int[offsets.length];

		for (int i = 0; i < offsets.length; i++) {
			heights[i] = level.getChunkSource().getGenerator().getFirstOccupiedHeight((located.getX() & ~15) + offsets[i][0],
					(located.getZ() & ~15) + offsets[i][1], Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
		}

		return HeightSpread.spread(heights);
	}

	/**
	 * How far above (positive) or below (negative) the terrain's own ground level the campfire
	 * stands. A camp sunk into a dip or perched on a mound gives a value away from 0.
	 */
	static int heightAboveGround(ServerLevel level, BlockPos campfire) {
		return campfire.getY() - level.getChunkSource().getGenerator().getFirstFreeHeight(campfire.getX(), campfire.getZ(),
				Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
	}

	static BlockPos findCampfire(ServerLevel level, BlockPos near) {
		for (BlockPos pos : BlockPos.betweenClosed(near.getX() - 16, level.getMinY(), near.getZ() - 16, near.getX() + 16, level.getMaxY(), near.getZ() + 16)) {
			if (level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
				return pos.immutable();
			}
		}

		return null;
	}

	/**
	 * Hovers the player above and to the north-west of the target, looking down at it. Spectator
	 * mode keeps the camera in place; in any other mode the player would fall before the picture.
	 */
	private static void lookAt(ClientGameTestContext context, TestSingleplayerContext world, String player, BlockPos target) {
		world.getServer().runCommand("gamemode spectator " + player);
		world.getServer().runCommand("tp " + player + " " + (target.getX() - 6.5) + " " + (target.getY() + 8) + " " + (target.getZ() - 6.5) + " -45 40");
		world.getConnection().waitForChunksRender();
		context.waitTicks(20);
	}

	/**
	 * Takes a picture once the chat lines from earlier commands have faded (they stay 10 seconds).
	 */
	private static void takeWorldScreenshot(ClientGameTestContext context, String name) {
		context.waitTicks(220);
		context.takeScreenshot(name);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
