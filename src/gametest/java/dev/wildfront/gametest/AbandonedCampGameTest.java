package dev.wildfront.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Checks that the abandoned camp's data is loaded by a real server and that its template builds
 * what {@code tools/generate_structures.py} describes.
 */
public class AbandonedCampGameTest {
	@GameTest
	public void worldgenDataIsRegistered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.ABANDONED_CAMP).isPresent(),
				"structure wildfront:abandoned_camp should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(WildfrontStructures.ABANDONED_CAMPS).isPresent(),
				"structure set wildfront:abandoned_camps should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).get(WildfrontStructures.ABANDONED_CAMP_START_POOL).isPresent(),
				"template pool wildfront:abandoned_camp/start should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.BIOME).get(WildfrontStructures.HAS_ABANDONED_CAMP)
				.map(biomes -> biomes.size() > 0).orElse(false), "the camp's biome tag should contain biomes");
		helper.succeed();
	}

	@GameTest
	public void templateBuildsTheCampWithLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureTemplateManager().get(WildfrontStructures.ABANDONED_CAMP_TEMPLATE).orElse(null);
		helper.assertTrue(template != null, "template wildfront:abandoned_camp/camp should load");
		helper.assertTrue(template.getSize().equals(new Vec3i(7, 4, 7)), "template should be 7x4x7 but was " + template.getSize());

		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2),
				"template should be placed");

		helper.assertBlockPresent(Blocks.PACKED_MUD, 3, 0, 3);
		helper.assertBlockPresent(Blocks.CAMPFIRE, 3, 1, 3);
		helper.assertFalse(level.getBlockState(helper.absolutePos(new BlockPos(3, 1, 3))).getValue(CampfireBlock.LIT), "the campfire should be cold");
		helper.assertBlockPresent(Blocks.SPRUCE_FENCE, 0, 1, 4);
		helper.assertBlockPresent(Blocks.SPRUCE_SLAB, 1, 2, 5);
		helper.assertBlockPresent(Blocks.HAY_BLOCK, 1, 1, 5);
		helper.assertBlockPresent(Blocks.SKELETON_SKULL, 5, 1, 5);
		helper.assertBlockPresent(Blocks.CRAFTING_TABLE, 5, 1, 1);
		// Positions the generator leaves out must keep whatever was there.
		helper.assertBlockPresent(Blocks.AIR, 0, 0, 0);
		helper.assertBlockPresent(Blocks.AIR, 3, 3, 3);

		helper.assertBlockPresent(Blocks.CHEST, 1, 1, 1);
		ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(1, 1, 1), ChestBlockEntity.class);
		helper.assertTrue(WildfrontLootTables.ABANDONED_CAMP.equals(chest.getLootTable()), "the chest should carry the camp loot table but had " + chest.getLootTable());

		chest.unpackLootTable(null);
		helper.assertFalse(chest.isEmpty(), "the camp loot table should generate at least one item");
		helper.succeed();
	}

	/**
	 * Each layout must be a loadable 7x4x7 template (the size the level-ground check assumes) with
	 * exactly one unlit campfire and exactly one loot chest: the other tests find camps by those.
	 */
	@GameTest
	public void everyLayoutHasACampfireAndOneLootChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);

		for (Identifier id : WildfrontStructures.ABANDONED_CAMP_LAYOUTS) {
			StructureTemplate template = level.getStructureTemplateManager().get(id).orElse(null);
			helper.assertTrue(template != null, "template " + id + " should load");
			helper.assertTrue(template.getSize().equals(new Vec3i(7, 4, 7)), "template " + id + " should be 7x4x7 but was " + template.getSize());

			for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(6, 3, 6))) {
				level.removeBlock(pos, false);
			}

			helper.killAllEntities();
			helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2), "template " + id + " should be placed");

			int campfires = 0;
			int lootChests = 0;
			int groundBlocks = 0;

			for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(6, 3, 6))) {
				BlockState state = level.getBlockState(pos);

				if (state.is(Blocks.CAMPFIRE)) {
					campfires++;
					helper.assertFalse(state.getValue(CampfireBlock.LIT), "the campfire of " + id + " should be cold");
					helper.assertTrue(pos.getY() == origin.getY() + 1, "the campfire of " + id + " should stand on the ground layer");
					helper.assertTrue(level.getBlockState(pos.below()).is(Blocks.PACKED_MUD), "the campfire of " + id + " should stand on packed mud");
				}

				if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && WildfrontLootTables.ABANDONED_CAMP.equals(chest.getLootTable())) {
					lootChests++;
					helper.assertFalse(level.getBlockState(pos.below()).isAir(), "the chest of " + id + " should stand on something");
					chest.unpackLootTable(null);
					helper.assertFalse(chest.isEmpty(), "the chest of " + id + " should generate loot");
					chest.clearContent();
				}

				if (pos.getY() == origin.getY() && !state.isAir()) {
					groundBlocks++;
					helper.assertFalse(state.is(BlockTags.SUPPORTS_VEGETATION), "the ground of " + id + " contains " + state.getBlock()
							+ ", which trees can take root in");
				}
			}

			helper.assertTrue(campfires == 1, id + " should have exactly one campfire but has " + campfires);
			helper.assertTrue(lootChests == 1, id + " should have exactly one loot chest but has " + lootChests);
			helper.assertTrue(groundBlocks >= 20, id + " should have a ground layer but has only " + groundBlocks + " ground blocks");
		}

		helper.succeed();
	}

	@GameTest
	public void startPoolOffersEveryLayout(GameTestHelper helper) {
		StructureTemplatePool pool = helper.getLevel().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL)
				.get(WildfrontStructures.ABANDONED_CAMP_START_POOL).orElseThrow().value();
		String elements = pool.getShuffledTemplates(helper.getLevel().getRandom()).toString();

		for (Identifier id : WildfrontStructures.ABANDONED_CAMP_LAYOUTS) {
			helper.assertTrue(elements.contains(id.toString()), "the camp's start pool should offer " + id + " but contains " + elements);
		}

		helper.succeed();
	}

	/**
	 * Generates the camp the way world generation and {@code /place structure} do: through the
	 * structure definition, its start pool and its height projection, with a random rotation.
	 */
	@GameTest
	public void structureGeneratesEmbeddedInTheGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(4, 0, 4));

		String command = "place structure wildfront:abandoned_camp " + center.getX() + " " + center.getY() + " " + center.getZ();

		try {
			level.getServer().getCommands().getDispatcher().execute(command, level.getServer().createCommandSourceStack().withSuppressedOutput());
		} catch (CommandSyntaxException e) {
			throw helper.assertionException("/" + command + " failed: " + e.getMessage());
		}

		BlockPos campfire = null;
		BlockPos chest = null;

		// Jigsaw structures start at the north-west corner of the chunk containing the requested
		// position and are rotated randomly around it, so that corner is where to look. The camp
		// generates on the test world's surface, which lies below every test area; looking only
		// there keeps the camp another test builds from its template out of the search.
		BlockPos start = new BlockPos(center.getX() & ~15, center.getY() - 1, center.getZ() & ~15);

		for (BlockPos pos : BlockPos.betweenClosed(start.offset(-9, -8, -9), start.offset(9, 0, 9))) {
			if (level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
				campfire = pos.immutable();
			}
		}

		helper.assertTrue(campfire != null, "the generated camp should contain its campfire");

		// The chest stands on the ground or, in the cart layout, two blocks up on the cart. Looking
		// no higher than that keeps chests that other tests place in their areas out of the search.
		for (BlockPos pos : BlockPos.betweenClosed(campfire.offset(-6, 0, -6), campfire.offset(6, 2, 6))) {
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity found && WildfrontLootTables.ABANDONED_CAMP.equals(found.getLootTable())) {
				chest = pos.immutable();
			}
		}

		helper.assertTrue(chest != null, "the generated camp should contain its loot chest");
		helper.assertTrue(level.getBlockState(campfire.below()).is(Blocks.PACKED_MUD), "the camp's ground layer should be under the campfire");
		helper.assertFalse(level.getBlockState(campfire.below(2)).isAir(), "the camp should rest on the terrain, not float above it");
		// The chunk corner can lie under a neighbouring test area, whose frame of barriers and
		// control block is not terrain.
		BlockState above = level.getBlockState(campfire.above());
		helper.assertTrue(above.isAir() || above.is(Blocks.BARRIER) || above.is(Blocks.TEST_INSTANCE_BLOCK),
				"the camp should not be buried, but " + above.getBlock() + " is above its campfire");
		// In most layouts the chest stands on the ground; in the cart layout it rides on the cart.
		helper.assertTrue(chest.getY() >= campfire.getY() && chest.getY() <= campfire.getY() + 2,
				"the chest should be at or just above the campfire's level, but campfire " + campfire + ", chest " + chest);

		// The template's bottom layer must replace the top block of the terrain. One block lower
		// and the whole camp sits in a dip that the terrain adaptation then digs around it.
		int groundLevel = level.getChunkSource().getGenerator().getFirstFreeHeight(campfire.getX(), campfire.getZ(),
				Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
		helper.assertTrue(campfire.getY() == groundLevel, "the campfire should stand at ground level " + groundLevel + " but is at " + campfire.getY());
		helper.succeed();
	}
}
