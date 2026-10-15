package dev.wildfront.gametest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import dev.wildfront.dungeon.DungeonState;
import dev.wildfront.dungeon.ForgottenTemple;
import dev.wildfront.dungeon.GlyphSequence;
import dev.wildfront.registry.WildfrontLootTables;
import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Forgotten Temple on a real server. Every variant is built in the open air, its clues are
 * read from its blocks the way a player would read them, and the puzzle is solved with what they
 * say. Built in the air, the temple is surrounded by nothing, the worst case for a temple that
 * a cave runs into: its sealed chambers must still be closed off.
 */
public class ForgottenTempleGameTest {
	private static final Vec3i SIZE = new Vec3i(21, 8, 21);

	/** Where a player comes in: the corridor's open end. */
	private static final BlockPos ENTRANCE = new BlockPos(10, 1, 20);
	private static final BlockPos SANCTUM_CHEST = new BlockPos(8, 1, 1);
	private static final BlockPos RELIQUARY_CHEST = new BlockPos(1, 1, 7);

	@GameTest
	public void worldgenDataIsRegistered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.FORGOTTEN_TEMPLE).isPresent(),
				"structure wildfront:forgotten_temple should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(WildfrontStructures.FORGOTTEN_TEMPLES).isPresent(),
				"structure set wildfront:forgotten_temples should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).get(WildfrontStructures.FORGOTTEN_TEMPLE_START_POOL).isPresent(),
				"template pool wildfront:forgotten_temple/start should be registered");
		helper.assertTrue(ForgottenTemple.SOLUTIONS.keySet().equals(Set.copyOf(WildfrontStructures.FORGOTTEN_TEMPLE_VARIANTS)),
				"every variant should have a solution and every solution a variant");

		for (ResourceKey<LootTable> table : List.of(WildfrontLootTables.TEMPLE_SANCTUM, WildfrontLootTables.TEMPLE_RELIQUARY)) {
			helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(table) != LootTable.EMPTY, "loot table " + table + " should load");
		}

		helper.succeed();
	}

	@GameTest
	public void variant1IsSolvableFromItsClues(GameTestHelper helper) {
		solveFromClues(helper, WildfrontStructures.FORGOTTEN_TEMPLE_VARIANTS.get(0), 40);
	}

	@GameTest
	public void variant2IsSolvableFromItsClues(GameTestHelper helper) {
		solveFromClues(helper, WildfrontStructures.FORGOTTEN_TEMPLE_VARIANTS.get(1), 52);
	}

	@GameTest
	public void variant3IsSolvableFromItsClues(GameTestHelper helper) {
		solveFromClues(helper, WildfrontStructures.FORGOTTEN_TEMPLE_VARIANTS.get(2), 64);
	}

	@GameTest
	public void templeStateSurvivesSavingAndLoading(GameTestHelper helper) {
		DungeonState state = new DungeonState(new BlockPos(100, -20, -300), 3, false, true);
		JsonElement saved = DungeonState.CODEC.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();
		helper.assertTrue(state.equals(DungeonState.CODEC.parse(JsonOps.INSTANCE, saved).result().orElse(null)), "a temple's state should survive saving and loading: " + saved);
		helper.succeed();
	}

	/**
	 * Builds the variant {@code height} blocks above the test, solves it from its clues and checks
	 * every step. Each variant is built at its own height, so tests running side by side do not
	 * build into each other.
	 */
	private static void solveFromClues(GameTestHelper helper, Identifier variant, int height) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureTemplateManager().get(variant).orElse(null);
		helper.assertTrue(template != null, "template " + variant + " should load");
		helper.assertTrue(template.getSize().equals(SIZE), "template " + variant + " should be " + SIZE + " but was " + template.getSize());

		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(height);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(SIZE.getX() - 1, SIZE.getY() - 1, SIZE.getZ() - 1));
		ForgottenTemple.Temple temple = new ForgottenTemple.Temple(box, variant);

		try {
			helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2), "template " + variant + " should be placed");

			// The clue: each mural glyph beside a plinth with lit candles. Their numbers give the order.
			List<Block> order = readOrderFromMurals(helper, level, box, variant);
			helper.assertTrue(order.equals(temple.solution()), variant + ": the murals say " + names(order) + " but the mod expects " + names(temple.solution()));

			Map<Block, BlockPos> pedestals = pedestalGlyphs(helper, level, box, variant);
			helper.assertTrue(count(level, box, ForgottenTemple.SEAL) == 9, variant + ": the sanctum's seal should be 3 by 3 crying obsidian");
			helper.assertTrue(count(level, box, ForgottenTemple.SECRET_SEAL) == 4, variant + ": the reliquary's seal should be 2 by 2 obsidian");
			helper.assertTrue(lootTableAt(level, origin.offset(SANCTUM_CHEST)) == WildfrontLootTables.TEMPLE_SANCTUM
					&& lootTableAt(level, origin.offset(SANCTUM_CHEST.offset(4, 0, 0))) == WildfrontLootTables.TEMPLE_SANCTUM, variant + ": the sanctum should hold two chests of its loot");
			helper.assertTrue(lootTableAt(level, origin.offset(RELIQUARY_CHEST)) == WildfrontLootTables.TEMPLE_RELIQUARY, variant + ": the reliquary should hold a chest of its loot");

			Set<BlockPos> reachable = reachableFrom(level, box, origin.offset(ENTRANCE));
			helper.assertTrue(reachable.contains(pedestals.get(ForgottenTemple.GLYPH_A).south()), variant + ": the pedestals should be reachable from the entrance");
			helper.assertFalse(reachable.contains(origin.offset(SANCTUM_CHEST)), variant + ": the sanctum must be closed off while its seal stands");
			helper.assertFalse(reachable.contains(origin.offset(RELIQUARY_CHEST)), variant + ": the reliquary must be closed off while its seal stands");
			helper.assertTrue(ForgottenTemple.isProtected(level, temple, Blocks.REINFORCED_DEEPSLATE.defaultBlockState())
					&& ForgottenTemple.isProtected(level, temple, Blocks.CRYING_OBSIDIAN.defaultBlockState())
					&& ForgottenTemple.isProtected(level, temple, ForgottenTemple.GLYPH_A.defaultBlockState())
					&& ForgottenTemple.isProtected(level, temple, Blocks.OBSIDIAN.defaultBlockState()), variant + ": the shell, seals and glyphs should be protected while sealed");
			helper.assertFalse(ForgottenTemple.isProtected(level, temple, Blocks.STONE_BRICKS.defaultBlockState()), variant + ": ordinary walls may be broken");

			// A wrong glyph first: nothing breaks, the order starts over.
			Block first = temple.solution().get(0);
			Block wrong = temple.solution().get(1);
			helper.assertTrue(ForgottenTemple.press(level, temple, pedestals.get(first), first) == GlyphSequence.Outcome.ADVANCED, variant + ": the first glyph should advance");
			helper.assertTrue(candleLit(level, pedestals.get(first)), variant + ": the first pedestal's candle should light");
			helper.assertTrue(ForgottenTemple.press(level, temple, pedestals.get(first), first) == GlyphSequence.Outcome.WRONG, variant + ": the same glyph twice should be a mistake");
			helper.assertFalse(candleLit(level, pedestals.get(first)), variant + ": a mistake should put the pedestal candles out");
			helper.assertTrue(ForgottenTemple.press(level, temple, pedestals.get(wrong), wrong) == GlyphSequence.Outcome.WRONG, variant + ": starting with the second glyph should be a mistake");
			helper.assertTrue(count(level, box, ForgottenTemple.SEAL) == 9, variant + ": mistakes must not touch the seal");

			// The order the murals give.
			for (int i = 0; i < order.size(); i++) {
				Block glyph = order.get(i);
				GlyphSequence.Outcome outcome = ForgottenTemple.press(level, temple, pedestals.get(glyph), glyph);
				helper.assertTrue(outcome == (i == order.size() - 1 ? GlyphSequence.Outcome.SOLVED : GlyphSequence.Outcome.ADVANCED),
						variant + ": glyph " + (i + 1) + " of the order should " + (i == order.size() - 1 ? "solve" : "advance") + " but gave " + outcome);
			}

			for (Block glyph : ForgottenTemple.GLYPHS) {
				helper.assertTrue(candleLit(level, pedestals.get(glyph)), variant + ": every pedestal's candle should burn once solved, but " + glyph + "'s does not");
			}

			helper.assertTrue(count(level, box, ForgottenTemple.SEAL) == 0, variant + ": the seal should be gone once solved");
			helper.assertTrue(ForgottenTemple.stateOf(level, temple).solved(), variant + ": the temple should be recorded as solved");
			helper.assertTrue(reachableFrom(level, box, origin.offset(ENTRANCE)).contains(origin.offset(SANCTUM_CHEST)), variant + ": the sanctum should be open once solved");
			helper.assertFalse(reachableFrom(level, box, origin.offset(ENTRANCE)).contains(origin.offset(RELIQUARY_CHEST)), variant + ": solving must not open the secret");
			helper.assertFalse(ForgottenTemple.isProtected(level, temple, ForgottenTemple.GLYPH_A.defaultBlockState()), variant + ": glyphs may be broken once solved");
			helper.assertTrue(ForgottenTemple.press(level, temple, pedestals.get(first), first) == GlyphSequence.Outcome.IGNORED, variant + ": presses after solving do nothing");

			// The secret: the fifth glyph, beside its seal.
			BlockPos secret = only(helper, level, box, ForgottenTemple.SECRET_GLYPH, variant, true);
			ForgottenTemple.press(level, temple, secret, ForgottenTemple.SECRET_GLYPH);
			helper.assertTrue(count(level, box, ForgottenTemple.SECRET_SEAL) == 0, variant + ": the secret seal should be gone");
			helper.assertTrue(reachableFrom(level, box, origin.offset(ENTRANCE)).contains(origin.offset(RELIQUARY_CHEST)), variant + ": the reliquary should be open");
			helper.assertTrue(ForgottenTemple.isProtected(level, temple, Blocks.REINFORCED_DEEPSLATE.defaultBlockState()), variant + ": the shell stays protected");
		} finally {
			ForgottenTemple.forget(level, temple);

			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}

		helper.succeed();
	}

	private static List<Block> readOrderFromMurals(GameTestHelper helper, ServerLevel level, BoundingBox box, Identifier variant) {
		Map<Integer, Block> byCandles = new HashMap<>();
		int secretMurals = 0;

		for (BlockPos pos : positions(box)) {
			BlockState state = level.getBlockState(pos);
			boolean onPlinth = level.getBlockState(pos.below()).is(Blocks.POLISHED_TUFF);

			if (!onPlinth) {
				continue;
			}

			for (Direction side : Direction.Plane.HORIZONTAL) {
				Block beside = level.getBlockState(pos.relative(side)).getBlock();

				if (state.getBlock() instanceof CandleBlock && state.getValue(CandleBlock.LIT) && ForgottenTemple.GLYPHS.contains(beside)) {
					Block before = byCandles.put(state.getValue(CandleBlock.CANDLES), beside);
					helper.assertTrue(before == null, variant + ": two murals have " + state.getValue(CandleBlock.CANDLES) + " candles");
				}

				if (state.isAir() && beside == ForgottenTemple.SECRET_GLYPH) {
					secretMurals++;
				}
			}
		}

		helper.assertTrue(byCandles.keySet().equals(Set.of(1, 2, 3, 4)), variant + ": the murals should have 1, 2, 3 and 4 candles but have " + byCandles.keySet());
		helper.assertTrue(Set.copyOf(byCandles.values()).equals(Set.copyOf(ForgottenTemple.GLYPHS)), variant + ": each glyph should have one mural");
		helper.assertTrue(secretMurals == 1, variant + ": the fifth glyph should have one mural without candles, but has " + secretMurals);
		return List.of(byCandles.get(1), byCandles.get(2), byCandles.get(3), byCandles.get(4));
	}

	/**
	 * @return each pedestal glyph's position; checks that each has exactly one button and an unlit candle
	 */
	private static Map<Block, BlockPos> pedestalGlyphs(GameTestHelper helper, ServerLevel level, BoundingBox box, Identifier variant) {
		Map<Block, BlockPos> pedestals = new HashMap<>();

		for (BlockPos pos : positions(box)) {
			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof ButtonBlock) {
				BlockPos support = supportOf(pos, state);
				Block glyph = level.getBlockState(support).getBlock();

				if (ForgottenTemple.GLYPHS.contains(glyph)) {
					helper.assertTrue(pedestals.put(glyph, support.immutable()) == null, variant + ": glyph " + glyph + " has two buttons");
					helper.assertTrue(level.getBlockState(support.above()).getBlock() instanceof CandleBlock && !candleLit(level, support),
							variant + ": each pedestal should carry an unlit candle");
				}
			}
		}

		helper.assertTrue(pedestals.keySet().equals(Set.copyOf(ForgottenTemple.GLYPHS)), variant + ": each glyph should have one pedestal with a button, but found " + names(List.copyOf(pedestals.keySet())));
		return pedestals;
	}

	private static BlockPos supportOf(BlockPos button, BlockState state) {
		AttachFace face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
		return face == AttachFace.FLOOR ? button.below() : face == AttachFace.CEILING ? button.above()
				: button.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
	}

	/**
	 * @return the single position of the block; with {@code button}, also checks a button is fixed to it
	 */
	private static BlockPos only(GameTestHelper helper, ServerLevel level, BoundingBox box, Block block, Identifier variant, boolean button) {
		List<BlockPos> found = new ArrayList<>();

		for (BlockPos pos : positions(box)) {
			if (level.getBlockState(pos).is(block)) {
				boolean hasButton = false;

				for (Direction side : Direction.values()) {
					BlockState next = level.getBlockState(pos.relative(side));
					hasButton |= next.getBlock() instanceof ButtonBlock && supportOf(pos.relative(side), next).equals(pos);
				}

				if (!button || hasButton) {
					found.add(pos.immutable());
				}
			}
		}

		helper.assertTrue(found.size() == 1, variant + ": expected one " + block + (button ? " with a button" : "") + " but found " + found.size());
		return found.get(0);
	}

	/**
	 * @return every position inside the box a player could walk or swim to from {@code start},
	 *         passing anything that is not a full block
	 */
	private static Set<BlockPos> reachableFrom(ServerLevel level, BoundingBox box, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);

		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();

			for (Direction side : Direction.values()) {
				BlockPos next = pos.relative(side);

				if (box.isInside(next) && !seen.contains(next) && !level.getBlockState(next).isCollisionShapeFullBlock(level, next)) {
					seen.add(next);
					queue.add(next);
				}
			}
		}

		return seen;
	}

	private static Iterable<BlockPos> positions(BoundingBox box) {
		return BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	private static int count(ServerLevel level, BoundingBox box, Block block) {
		int count = 0;

		for (BlockPos pos : positions(box)) {
			if (level.getBlockState(pos).is(block)) {
				count++;
			}
		}

		return count;
	}

	private static boolean candleLit(ServerLevel level, BlockPos glyph) {
		BlockState candle = level.getBlockState(glyph.above());
		return candle.getBlock() instanceof CandleBlock && candle.getValue(CandleBlock.LIT);
	}

	private static ResourceKey<LootTable> lootTableAt(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof ChestBlockEntity chest ? chest.getLootTable() : null;
	}

	private static String names(List<Block> blocks) {
		return blocks.stream().map(block -> Optional.ofNullable(block).map(Block::getDescriptionId).orElse("none")).toList().toString();
	}
}
