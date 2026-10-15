package dev.wildfront.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.wildfront.camp.CampManager;
import dev.wildfront.camp.CampState;
import dev.wildfront.config.ConfigManager;
import dev.wildfront.discovery.ActiveCache;
import dev.wildfront.discovery.DiscoveryManager;
import dev.wildfront.dungeon.DungeonStates;
import dev.wildfront.dungeon.ForgottenTemple;
import dev.wildfront.dungeon.MechanistsVault;
import dev.wildfront.dungeon.VaultCircuit;
import dev.wildfront.entity.GuardEntity;
import dev.wildfront.registry.WildfrontEntities;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;
import dev.wildfront.worldgen.HeightSpread;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
			naturalForgottenTemple(context, world, player);
			naturalMechanistsVault(context, world, player);
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
		// A chief beside it; there is no command for one, camps make them.
		server.computeOnServer(s -> {
			GuardEntity chief = WildfrontEntities.GUARD.create(s.overworld(), EntitySpawnReason.COMMAND);
			chief.snapTo(x + 2.5, y + 1, z + 0.5, 180.0F, 0.0F);
			chief.setYHeadRot(180.0F);
			chief.setYBodyRot(180.0F);
			chief.makeChief();
			chief.setNoAi(true);
			chief.setPersistenceRequired();
			return s.overworld().addFreshEntity(chief);
		});

		try {
			List<ItemStack> held = server.computeOnServer(s -> s.overworld().getEntities(EntityTypeTest.forClass(GuardEntity.class), guard -> !guard.isChief()).stream()
					.map(guard -> guard.getMainHandItem().copy()).toList());
			check(held.size() == 1, "the summon command should create exactly one guard, but there are " + held.size());
			check(held.get(0).is(Items.IRON_SWORD), "a summoned guard should carry an iron sword but holds " + held.get(0));

			server.runCommand("tp " + player + " " + (x + 1.5) + " " + (y + 1.4) + " " + (z - 3.3) + " 0 5");
			world.getConnection().waitForChunksRender();
			// Long enough for the chat lines of the commands above to fade.
			takeWorldScreenshot(context, "0_guard_front");

			server.runCommand("tp " + player + " " + (x + 1.5) + " " + (y + 1.4) + " " + (z + 4.3) + " 180 5");
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
					{ "checkIntervalSeconds": 1, "events": { "mysterious_discovery": { "enabled": false } }, "hostileCamps": { "chiefChance": 1 } }
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
			// The test config makes every camp a chief's camp: 2 or 3 guards and the chief.
			check(occupied.chief() && occupied.stash().isPresent(), "with a chief chance of 1 the camp should be led by a chief and have a stash");
			check(occupied.guardsLeft() >= 3 && occupied.guardsLeft() <= 4, "a small camp should have 2 or 3 guards and its chief, but has " + occupied.guardsLeft());
			check(alive == occupied.guardsLeft(), "the camp records " + occupied.guardsLeft() + " guard(s) but " + alive + " are alive");
			BlockPos stash = occupied.stash().get();
			check(server.computeOnServer(s -> s.overworld().getBlockState(stash).is(Blocks.BARREL) && CampManager.isBarred(s.overworld(), stash)),
					"the camp's barrel should be its barred stash");

			server.runCommand("tp " + player + " " + (fire.getX() + 9.5) + " " + (fire.getY() + 7) + " " + (fire.getZ() + 9.5) + " 135 35");
			world.getConnection().waitForChunksRender();
			takeWorldScreenshot(context, "1_bandit_camp");
			check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypeTest.forClass(GuardEntity.class), GuardEntity::isChief).stream()
					.anyMatch(chief -> chief.showsBossBarTo(s.getPlayerList().getPlayerByName(player)))), "a player near the camp should see the chief's boss bar");

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
			check(back >= 3 && back <= 4 && server.computeOnServer(s -> countGuards(s.overworld())) == back,
					"after peaceful the camp should be occupied by its guards and chief again, but it records " + back);
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
	 * Surveys where temples generate, then goes to the nearest one, checks what generated there and
	 * solves it the way a player does: through the same block events, in the order its murals give.
	 */
	private static void naturalForgottenTemple(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		CampSurvey.Result survey = server.computeOnServer(s -> CampSurvey.run(s.overworld(), s.getPlayerList().getPlayers().get(0).blockPosition(),
				WildfrontStructures.FORGOTTEN_TEMPLE, WildfrontStructures.FORGOTTEN_TEMPLE_VARIANTS, 3, SURVEY_MAX_CANDIDATES));
		System.out.println("[WildfrontClientGameTest] temple survey: " + survey.camps().size() + " temples at " + survey.candidates()
				+ " candidate spots (" + Math.round(100.0 * survey.camps().size() / survey.candidates()) + "%), by variant: " + survey.countByLayout());
		check(!survey.camps().isEmpty(), "world generation should place temples, but none of " + survey.candidates() + " candidate spots has one");

		BlockPos located = survey.camps().get(0).start();
		server.runCommand("gamemode spectator " + player);
		server.runCommand("effect give " + player + " minecraft:night_vision 300 0 true");
		server.runCommand("tp " + player + " " + located.getX() + " " + (located.getY() + 4) + " " + located.getZ());
		waitForTerrain(context, world, located);

		ForgottenTemple.Temple temple = server.computeOnServer(s -> ForgottenTemple.find(s.overworld(), located).orElse(null));
		check(temple != null, "the temple at " + located.toShortString() + " should be found from its own corner");
		check(temple.solution().size() == 4, "the temple's variant " + temple.variant() + " should have a known order");
		int seals = server.computeOnServer(s -> countIn(s.overworld(), temple.box(), ForgottenTemple.SEAL));
		int secretSeals = server.computeOnServer(s -> countIn(s.overworld(), temple.box(), ForgottenTemple.SECRET_SEAL));
		int fluids = server.computeOnServer(s -> {
			int wet = 0;

			for (BlockPos pos : BlockPos.betweenClosed(temple.box().minX(), temple.box().minY(), temple.box().minZ(), temple.box().maxX(), temple.box().maxY(), temple.box().maxZ())) {
				wet += s.overworld().getFluidState(pos).isEmpty() ? 0 : 1;
			}

			return wet;
		});
		System.out.println("[WildfrontClientGameTest] natural temple " + temple.variant() + " at " + temple.box().getCenter().toShortString()
				+ ": seal " + seals + " block(s), secret seal " + secretSeals + ", water or lava in " + fluids + " block(s) of its box");
		check(seals == 9 && secretSeals == 4, "the temple's seals should have generated whole, but there are " + seals + " and " + secretSeals + " blocks");

		// Where the pedestals' buttons are, and where to stand to look at the seal.
		Map<Block, BlockPos> buttons = server.computeOnServer(s -> pedestalButtons(s.overworld(), temple));
		check(buttons.keySet().equals(Set.copyOf(ForgottenTemple.GLYPHS)), "the temple should have a button on each pedestal glyph, but has " + buttons.keySet());
		BlockPos seal = server.computeOnServer(s -> middleOf(s.overworld(), temple, ForgottenTemple.SEAL));
		BlockPos pedestals = average(List.copyOf(buttons.values()));
		double dx = pedestals.getX() - seal.getX();
		double dz = pedestals.getZ() - seal.getZ();
		double length = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		double cameraX = seal.getX() + 0.5 + dx / length * 7.0;
		double cameraZ = seal.getZ() + 0.5 + dz / length * 7.0;
		float yaw = (float) Math.toDegrees(Math.atan2(-(seal.getX() - cameraX + 0.5), seal.getZ() - cameraZ + 0.5));
		String camera = cameraX + " " + (seal.getY() + 0.5) + " " + cameraZ + " " + yaw + " 10";

		server.runCommand("tp " + player + " " + camera);
		world.getConnection().waitForChunksRender();
		takeWorldScreenshot(context, "1_temple_sealed");

		// As a survival player: the seal holds, then the glyphs are woken in the murals' order.
		server.runCommand("gamemode survival " + player);
		boolean sealHolds = server.computeOnServer(s -> {
			ServerPlayer serverPlayer = s.getPlayerList().getPlayerByName(player);
			BlockPos sealBlock = middleOf(s.overworld(), temple, ForgottenTemple.SEAL);
			return !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(s.overworld(), serverPlayer, sealBlock, s.overworld().getBlockState(sealBlock), null);
		});
		check(sealHolds, "a survival player must not be able to break the temple's seal");

		for (Block glyph : temple.solution()) {
			BlockPos button = buttons.get(glyph);
			server.computeOnServer(s -> UseBlockCallback.EVENT.invoker().interact(s.getPlayerList().getPlayerByName(player), s.overworld(),
					InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(button), Direction.UP, button, false)));
		}

		server.runCommand("gamemode spectator " + player);
		check(server.computeOnServer(s -> countIn(s.overworld(), temple.box(), ForgottenTemple.SEAL)) == 0, "pressing the glyphs in the murals' order should open the seal");
		check(server.computeOnServer(s -> ForgottenTemple.stateOf(s.overworld(), temple).solved()), "the temple should be recorded as solved");
		System.out.println("[WildfrontClientGameTest] natural temple solved through the block events in the order " + temple.solution());
		String candlesAfterSolving = server.computeOnServer(s -> pedestalCandles(s.overworld(), buttons));
		System.out.println("[WildfrontClientGameTest] pedestal candles after solving: " + candlesAfterSolving);

		server.runCommand("tp " + player + " " + camera);
		takeWorldScreenshot(context, "1_temple_open");
		String candlesAfterPicture = server.computeOnServer(s -> pedestalCandles(s.overworld(), buttons));
		System.out.println("[WildfrontClientGameTest] pedestal candles after the picture: " + candlesAfterPicture);
		check(!candlesAfterSolving.contains("unlit"), "every pedestal candle should burn once the temple is solved: " + candlesAfterSolving);
		server.runCommand("effect clear " + player + " minecraft:night_vision");
	}

	/**
	 * Surveys where vaults generate, goes to the nearest one and opens both its doors the way a
	 * player does: pulling levers, through the same block events, as the marks and the floor say.
	 */
	private static void naturalMechanistsVault(ClientGameTestContext context, TestSingleplayerContext world, String player) {
		TestServerContext server = world.getServer();
		CampSurvey.Result survey = server.computeOnServer(s -> CampSurvey.run(s.overworld(), s.getPlayerList().getPlayers().get(0).blockPosition(),
				WildfrontStructures.MECHANISTS_VAULT, WildfrontStructures.MECHANISTS_VAULT_VARIANTS, 3, SURVEY_MAX_CANDIDATES));
		System.out.println("[WildfrontClientGameTest] vault survey: " + survey.camps().size() + " vaults at " + survey.candidates()
				+ " candidate spots (" + Math.round(100.0 * survey.camps().size() / survey.candidates()) + "%), by variant: " + survey.countByLayout());
		check(!survey.camps().isEmpty(), "world generation should place vaults, but none of " + survey.candidates() + " candidate spots has one");

		BlockPos located = survey.camps().get(0).start();
		server.runCommand("gamemode spectator " + player);
		server.runCommand("effect give " + player + " minecraft:night_vision 300 0 true");
		server.runCommand("tp " + player + " " + located.getX() + " " + (located.getY() + 4) + " " + located.getZ());
		waitForTerrain(context, world, located);

		MechanistsVault.Vault vault = server.computeOnServer(s -> MechanistsVault.find(s.overworld(), located).orElse(null));
		check(vault != null, "the vault at " + located.toShortString() + " should be found from its own corner");
		int doors = server.computeOnServer(s -> countIn(s.overworld(), vault.box(), MechanistsVault.DOOR));
		int secretDoors = server.computeOnServer(s -> countIn(s.overworld(), vault.box(), MechanistsVault.SECRET_DOOR));
		// Water or lava below the shed's floor is inside the buried part; above it, it is the surface around the shed.
		int[] fluids = server.computeOnServer(s -> {
			int[] wet = new int[2];

			for (BlockPos pos : BlockPos.betweenClosed(vault.box().minX(), vault.box().minY(), vault.box().minZ(), vault.box().maxX(), vault.box().maxY(), vault.box().maxZ())) {
				if (!s.overworld().getFluidState(pos).isEmpty()) {
					wet[pos.getY() < vault.box().minY() + 14 ? 0 : 1]++;
				}
			}

			return wet;
		});
		System.out.println("[WildfrontClientGameTest] natural vault " + vault.variant() + " at " + vault.box().getCenter().toShortString()
				+ ": door " + doors + " block(s), compartment door " + secretDoors + ", water or lava in " + fluids[0] + " block(s) below ground and "
				+ fluids[1] + " on the surface of its box");
		// Where a player can be: everything reachable from the desk without passing a full block.
		List<BlockPos> flooded = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos start = vaultLevers(level, vault).get(0);
			java.util.Set<BlockPos> seen = new java.util.HashSet<>(List.of(start));
			java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(List.of(start));
			List<BlockPos> wet = new ArrayList<>();

			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();

				if (!level.getFluidState(pos).isEmpty()) {
					wet.add(pos);
				}

				for (Direction side : Direction.values()) {
					BlockPos next = pos.relative(side);

					if (vault.box().isInside(next) && next.getY() < vault.box().minY() + 14 && seen.add(next)
							&& !level.getBlockState(next).isCollisionShapeFullBlock(level, next)) {
						queue.add(next);
					}
				}
			}

			return wet;
		});
		check(flooded.isEmpty(), "the vault's rooms should be dry, but water or lava stands at " + flooded);
		check(doors == 4 && secretDoors == 4, "the vault's doors should have generated whole, but there are " + doors + " and " + secretDoors + " blocks");

		Map<Integer, BlockPos> levers = server.computeOnServer(s -> vaultLevers(s.overworld(), vault));
		BlockPos testSwitch = levers.remove(-1);
		check(levers.size() == VaultCircuit.LEVERS && testSwitch != null, "the vault should have five levers on copper and a test switch, but has " + levers.keySet());

		BlockPos door = server.computeOnServer(s -> middleOfVault(s.overworld(), vault, MechanistsVault.DOOR));
		BlockPos desk = average(List.copyOf(levers.values()));
		double dx = desk.getX() - door.getX();
		double dz = desk.getZ() - door.getZ();
		double length = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		double cameraX = door.getX() + 0.5 + dx / length * 7.0;
		double cameraZ = door.getZ() + 0.5 + dz / length * 7.0;
		float yaw = (float) Math.toDegrees(Math.atan2(-(door.getX() - cameraX + 0.5), door.getZ() - cameraZ + 0.5)) - 20.0F;
		String camera = cameraX + " " + (door.getY() + 1.2) + " " + cameraZ + " " + yaw + " 12";

		server.runCommand("tp " + player + " " + camera);
		world.getConnection().waitForChunksRender();
		takeWorldScreenshot(context, "1_vault_closed");

		VaultCircuit circuit = vault.circuit();
		boolean[] solution = circuit.solutions(circuit.target()).get(0);
		server.runCommand("gamemode survival " + player);
		boolean doorHolds = server.computeOnServer(s -> !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(s.overworld(),
				s.getPlayerList().getPlayerByName(player), door, s.overworld().getBlockState(door), null));
		check(doorHolds, "a survival player must not be able to break the vault door");

		for (int lever = 0; lever < VaultCircuit.LEVERS; lever++) {
			if (solution[lever]) {
				pullLever(server, player, levers.get(lever));
			}
		}

		context.waitTicks(5);
		check(server.computeOnServer(s -> countIn(s.overworld(), vault.box(), MechanistsVault.DOOR)) == 0, "pulling the levers that match the marks should open the vault");
		check(server.computeOnServer(s -> DungeonStates.of(s.overworld(), vault.origin()).solved()), "the vault should be recorded as open");
		System.out.println("[WildfrontClientGameTest] natural vault opened by pulling levers " + java.util.Arrays.toString(solution));

		server.runCommand("gamemode spectator " + player);
		server.runCommand("tp " + player + " " + camera);
		takeWorldScreenshot(context, "1_vault_open");

		// The secret: the test switch, then the levers for the floor's pattern.
		boolean[] maintenance = circuit.solutions(circuit.maintenance()).get(0);
		server.runCommand("gamemode survival " + player);
		pullLever(server, player, testSwitch);

		for (int lever = 0; lever < VaultCircuit.LEVERS; lever++) {
			if (solution[lever] != maintenance[lever]) {
				pullLever(server, player, levers.get(lever));
			}
		}

		context.waitTicks(5);
		server.runCommand("gamemode spectator " + player);
		check(server.computeOnServer(s -> countIn(s.overworld(), vault.box(), MechanistsVault.SECRET_DOOR)) == 0, "the test switch and the floor's pattern should open the compartment");
		System.out.println("[WildfrontClientGameTest] natural vault's compartment opened");
		server.runCommand("effect clear " + player + " minecraft:night_vision");
	}

	/**
	 * Pulls a lever the way a player's click does: the block event first, then the lever flips.
	 */
	static void pullLever(TestServerContext server, String player, BlockPos lever) {
		server.computeOnServer(s -> {
			ServerPlayer serverPlayer = s.getPlayerList().getPlayerByName(player);
			UseBlockCallback.EVENT.invoker().interact(serverPlayer, s.overworld(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(lever), Direction.UP, lever, false));
			BlockState state = s.overworld().getBlockState(lever);
			((LeverBlock) state.getBlock()).pull(state, s.overworld(), lever, serverPlayer);
			return true;
		});
	}

	/**
	 * @return each lever on copper by its number; the test switch under -1
	 */
	static Map<Integer, BlockPos> vaultLevers(ServerLevel level, MechanistsVault.Vault vault) {
		Map<Integer, BlockPos> levers = new HashMap<>();

		for (BlockPos pos : BlockPos.betweenClosed(vault.box().minX(), vault.box().minY(), vault.box().minZ(), vault.box().maxX(), vault.box().maxY(), vault.box().maxZ())) {
			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof LeverBlock) {
				AttachFace face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
				BlockPos support = face == AttachFace.FLOOR ? pos.below() : face == AttachFace.CEILING ? pos.above()
						: pos.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
				Block mount = level.getBlockState(support).getBlock();
				int lever = mount == MechanistsVault.TEST_MOUNT ? -1 : MechanistsVault.LEVER_MOUNTS.indexOf(mount);

				if (lever >= -1 && (lever >= 0 || mount == MechanistsVault.TEST_MOUNT)) {
					levers.put(lever, pos.immutable());
				}
			}
		}

		return levers;
	}

	static BlockPos middleOfVault(ServerLevel level, MechanistsVault.Vault vault, Block block) {
		List<BlockPos> found = new ArrayList<>();

		for (BlockPos pos : BlockPos.betweenClosed(vault.box().minX(), vault.box().minY(), vault.box().minZ(), vault.box().maxX(), vault.box().maxY(), vault.box().maxZ())) {
			if (level.getBlockState(pos).is(block)) {
				found.add(pos.immutable());
			}
		}

		return found.isEmpty() ? vault.box().getCenter() : average(found);
	}

	static Map<Block, BlockPos> pedestalButtons(ServerLevel level, ForgottenTemple.Temple temple) {
		Map<Block, BlockPos> buttons = new HashMap<>();

		for (BlockPos pos : BlockPos.betweenClosed(temple.box().minX(), temple.box().minY(), temple.box().minZ(), temple.box().maxX(), temple.box().maxY(), temple.box().maxZ())) {
			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof ButtonBlock) {
				Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
				Block glyph = level.getBlockState(pos.relative(facing.getOpposite())).getBlock();

				if (ForgottenTemple.GLYPHS.contains(glyph)) {
					buttons.put(glyph, pos.immutable());
				}
			}
		}

		return buttons;
	}

	private static String pedestalCandles(ServerLevel level, Map<Block, BlockPos> buttons) {
		List<String> states = new ArrayList<>();

		for (Map.Entry<Block, BlockPos> entry : buttons.entrySet()) {
			BlockState button = level.getBlockState(entry.getValue());
			BlockPos glyph = entry.getValue().relative(button.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
			BlockState candle = level.getBlockState(glyph.above());
			states.add(entry.getKey().getDescriptionId() + " at " + glyph.toShortString() + ": "
					+ (candle.getBlock() instanceof CandleBlock ? (candle.getValue(CandleBlock.LIT) ? "lit" : "unlit") : candle.toString()));
		}

		return states.toString();
	}

	static BlockPos middleOf(ServerLevel level, ForgottenTemple.Temple temple, Block block) {
		List<BlockPos> found = new ArrayList<>();

		for (BlockPos pos : BlockPos.betweenClosed(temple.box().minX(), temple.box().minY(), temple.box().minZ(), temple.box().maxX(), temple.box().maxY(), temple.box().maxZ())) {
			if (level.getBlockState(pos).is(block)) {
				found.add(pos.immutable());
			}
		}

		return found.isEmpty() ? temple.box().getCenter() : average(found);
	}

	private static BlockPos average(List<BlockPos> positions) {
		long x = 0;
		long y = 0;
		long z = 0;

		for (BlockPos pos : positions) {
			x += pos.getX();
			y += pos.getY();
			z += pos.getZ();
		}

		int n = positions.size();
		return new BlockPos(Math.round((float) x / n), Math.round((float) y / n), Math.round((float) z / n));
	}

	private static int countIn(ServerLevel level, BoundingBox box, Block block) {
		int count = 0;

		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(pos).is(block)) {
				count++;
			}
		}

		return count;
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
