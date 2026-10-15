package dev.wildfront.gametest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.wildfront.dungeon.DungeonStates;
import dev.wildfront.dungeon.MechanistsVault;
import dev.wildfront.dungeon.VaultCircuit;
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
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Mechanist's Vault on a real server. Every variant is built in the open air, its marks and
 * floor pattern are read from its blocks the way a player sees them, and both doors are opened
 * with the levers alone. Built in the air, the vault is surrounded by nothing, so its sealed
 * rooms must be closed off on their own.
 */
public class MechanistsVaultGameTest {
	private static final Vec3i SIZE = new Vec3i(15, 18, 15);

	/** Where a player comes in: the shed's door. */
	private static final BlockPos ENTRANCE = new BlockPos(6, 15, 14);
	private static final BlockPos VAULT_CHEST = new BlockPos(13, 1, 8);
	private static final BlockPos COMPARTMENT_CHEST = new BlockPos(1, 1, 8);

	@GameTest
	public void worldgenDataIsRegistered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.MECHANISTS_VAULT).isPresent(),
				"structure wildfront:mechanists_vault should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(WildfrontStructures.MECHANISTS_VAULTS).isPresent(),
				"structure set wildfront:mechanists_vaults should be registered");
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).get(WildfrontStructures.MECHANISTS_VAULT_START_POOL).isPresent(),
				"template pool wildfront:mechanists_vault/start should be registered");

		for (Identifier variant : WildfrontStructures.MECHANISTS_VAULT_VARIANTS) {
			helper.assertTrue(new MechanistsVault.Vault(BoundingBox.fromCorners(BlockPos.ZERO, BlockPos.ZERO), variant).circuit() != null,
					"variant " + variant + " should have a circuit");
		}

		for (ResourceKey<LootTable> table : List.of(WildfrontLootTables.VAULT_MAIN, WildfrontLootTables.VAULT_COMPARTMENT)) {
			helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(table) != LootTable.EMPTY, "loot table " + table + " should load");
		}

		helper.succeed();
	}

	@GameTest
	public void variant1IsSolvableFromItsClues(GameTestHelper helper) {
		solveFromClues(helper, WildfrontStructures.MECHANISTS_VAULT_VARIANTS.get(0), 90);
	}

	@GameTest
	public void variant2IsSolvableFromItsClues(GameTestHelper helper) {
		solveFromClues(helper, WildfrontStructures.MECHANISTS_VAULT_VARIANTS.get(1), 110);
	}

	@GameTest
	public void variant3IsSolvableFromItsClues(GameTestHelper helper) {
		solveFromClues(helper, WildfrontStructures.MECHANISTS_VAULT_VARIANTS.get(2), 130);
	}

	/**
	 * Builds the variant {@code height} blocks above the test and opens both of its doors. Each
	 * variant is built at its own height, above where the temple tests build, so tests running
	 * side by side do not build into each other.
	 */
	private static void solveFromClues(GameTestHelper helper, Identifier variant, int height) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureTemplateManager().get(variant).orElse(null);
		helper.assertTrue(template != null, "template " + variant + " should load");
		helper.assertTrue(template.getSize().equals(SIZE), "template " + variant + " should be " + SIZE + " but was " + template.getSize());

		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(height);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(SIZE.getX() - 1, SIZE.getY() - 1, SIZE.getZ() - 1));
		MechanistsVault.Vault vault = new MechanistsVault.Vault(box, variant);
		VaultCircuit circuit = vault.circuit();

		try {
			helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2), "template " + variant + " should be placed");

			// The clues, read from the blocks: the mark above each gauge and the floor tile in front of it.
			Set<Integer> marked = new HashSet<>();
			Set<Integer> floor = new HashSet<>();
			Map<Integer, BlockPos> gauges = new HashMap<>();

			for (BlockPos pos : positions(box)) {
				if (!level.getBlockState(pos).is(MechanistsVault.GAUGE)) {
					continue;
				}

				int gauge = MechanistsVault.GAUGE_MARKERS.indexOf(level.getBlockState(pos.below()).getBlock());
				helper.assertTrue(gauge >= 0 && gauges.put(gauge, pos.immutable()) == null, variant + ": every gauge should stand on its own marker stone");
				BlockState mark = level.getBlockState(pos.above());
				helper.assertTrue(mark.is(Blocks.GLOWSTONE) || mark.is(MechanistsVault.CLUES.get(1)), variant + ": each gauge should have a mark above it");

				if (mark.is(Blocks.GLOWSTONE)) {
					marked.add(gauge);
				}

				BlockPos front = roomSide(level, pos);
				BlockState tile = level.getBlockState(front.below(3));
				helper.assertTrue(tile.is(Blocks.SEA_LANTERN) || tile.is(Blocks.SMOOTH_BASALT), variant + ": each gauge should have a floor tile in front of it, found " + tile);

				if (tile.is(Blocks.SEA_LANTERN)) {
					floor.add(gauge);
				}
			}

			helper.assertTrue(gauges.size() == VaultCircuit.GAUGES, variant + ": there should be six gauges but there are " + gauges.size());
			helper.assertTrue(marked.equals(circuit.target()), variant + ": the marks show " + marked + " but the mod expects " + circuit.target());
			helper.assertTrue(floor.equals(circuit.maintenance()), variant + ": the floor shows " + floor + " but the mod expects " + circuit.maintenance());

			Map<Integer, BlockPos> levers = new HashMap<>();
			BlockPos testSwitch = null;

			for (BlockPos pos : positions(box)) {
				BlockState state = level.getBlockState(pos);

				if (state.getBlock() instanceof LeverBlock) {
					Block mount = level.getBlockState(supportOf(pos, state)).getBlock();
					int lever = MechanistsVault.LEVER_MOUNTS.indexOf(mount);

					if (lever >= 0) {
						helper.assertTrue(levers.put(lever, pos.immutable()) == null, variant + ": two levers on the same copper");
					} else if (mount == MechanistsVault.TEST_MOUNT) {
						testSwitch = pos.immutable();
					}
				}
			}

			helper.assertTrue(levers.size() == VaultCircuit.LEVERS && testSwitch != null, variant + ": there should be five levers on copper and a test switch");

			Set<BlockPos> reachable = reachableFrom(level, box, origin.offset(ENTRANCE));
			helper.assertTrue(levers.values().stream().allMatch(reachable::contains), variant + ": every lever should be reachable from the shed");
			helper.assertTrue(reachable.contains(testSwitch), variant + ": the test switch should be reachable from the shaft");
			helper.assertFalse(reachable.contains(origin.offset(VAULT_CHEST)), variant + ": the vault must be closed off while its door stands");
			helper.assertFalse(reachable.contains(origin.offset(COMPARTMENT_CHEST)), variant + ": the compartment must be closed off while its door stands");
			helper.assertTrue(lootTableAt(level, origin.offset(VAULT_CHEST)) == WildfrontLootTables.VAULT_MAIN, variant + ": the vault should hold its loot");
			helper.assertTrue(lootTableAt(level, origin.offset(COMPARTMENT_CHEST)) == WildfrontLootTables.VAULT_COMPARTMENT, variant + ": the compartment should hold its loot");
			helper.assertTrue(MechanistsVault.isProtected(level, vault, Blocks.REINFORCED_DEEPSLATE.defaultBlockState())
					&& MechanistsVault.isProtected(level, vault, Blocks.OBSIDIAN.defaultBlockState())
					&& MechanistsVault.isProtected(level, vault, Blocks.CRYING_OBSIDIAN.defaultBlockState())
					&& MechanistsVault.isProtected(level, vault, MechanistsVault.GAUGE.defaultBlockState())
					&& MechanistsVault.isProtected(level, vault, Blocks.LEVER.defaultBlockState()), variant + ": doors, shell and machinery should be protected while closed");
			helper.assertFalse(MechanistsVault.isProtected(level, vault, Blocks.DEEPSLATE_TILES.defaultBlockState()), variant + ": ordinary walls may be broken");

			// Lighting the gauges some other way opens nothing: the vault reads the levers.
			for (int gauge : circuit.target()) {
				BlockPos pos = gauges.get(gauge);
				level.setBlock(pos, level.getBlockState(pos).setValue(CopperBulbBlock.LIT, true), 2);
			}

			MechanistsVault.Update cheat = MechanistsVault.update(level, vault);
			helper.assertFalse(cheat.opened(), variant + ": lit gauges without the levers must not open the vault");
			helper.assertTrue(gauges.values().stream().noneMatch(pos -> level.getBlockState(pos).getValue(CopperBulbBlock.LIT)), variant + ": the gauges should follow the levers again");

			// Each lever alone shows what it switches, and pulling it back undoes it.
			for (int lever = 0; lever < VaultCircuit.LEVERS; lever++) {
				setLever(level, levers.get(lever), true);
				boolean[] shown = MechanistsVault.update(level, vault).gauges();

				for (int gauge = 0; gauge < VaultCircuit.GAUGES; gauge++) {
					helper.assertTrue(shown[gauge] == circuit.leverGauges().get(lever).contains(gauge) && lit(level, gauges.get(gauge)) == shown[gauge],
							variant + ": lever " + lever + " alone should light exactly its own gauges");
				}

				setLever(level, levers.get(lever), false);
				helper.assertFalse(anyLit(MechanistsVault.update(level, vault).gauges()), variant + ": pulling lever " + lever + " back should put its gauges out");
			}

			// The maintenance pattern without the test switch opens nothing.
			boolean[] maintenance = circuit.solutions(circuit.maintenance()).get(0);
			setLevers(level, levers, maintenance);
			MechanistsVault.Update withoutSwitch = MechanistsVault.update(level, vault);
			helper.assertFalse(withoutSwitch.secretOpened() || withoutSwitch.opened(), variant + ": the maintenance pattern alone should open nothing");
			helper.assertTrue(count(level, box, MechanistsVault.SECRET_DOOR) == 4, variant + ": the compartment door should stand");

			// The target opens the vault.
			boolean[] solution = circuit.solutions(circuit.target()).get(0);
			setLevers(level, levers, solution);
			helper.assertTrue(MechanistsVault.update(level, vault).opened(), variant + ": the levers that make the gauges match their marks should open the vault");
			helper.assertTrue(count(level, box, MechanistsVault.DOOR) == 0, variant + ": the vault door should be gone");
			helper.assertTrue(DungeonStates.of(level, vault.origin()).solved(), variant + ": the vault should be recorded as opened");
			helper.assertTrue(reachableFrom(level, box, origin.offset(ENTRANCE)).contains(origin.offset(VAULT_CHEST)), variant + ": the vault should be reachable once open");
			helper.assertFalse(reachableFrom(level, box, origin.offset(ENTRANCE)).contains(origin.offset(COMPARTMENT_CHEST)), variant + ": opening the vault must not open the compartment");
			helper.assertFalse(MechanistsVault.update(level, vault).opened(), variant + ": an open vault does not open again");

			// A lever broken off and put back works as before.
			level.setBlock(levers.get(0), Blocks.AIR.defaultBlockState(), 3);
			MechanistsVault.update(level, vault);
			level.setBlock(levers.get(0), Blocks.LEVER.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR), 3);

			// The test switch and the floor's pattern open the compartment.
			setLever(level, testSwitch, true);
			setLevers(level, levers, maintenance);
			MechanistsVault.Update secret = MechanistsVault.update(level, vault);
			helper.assertTrue(secret.testSwitch() && secret.secretOpened(), variant + ": the test switch and the floor's pattern should open the compartment");
			helper.assertTrue(count(level, box, MechanistsVault.SECRET_DOOR) == 0, variant + ": the compartment door should be gone");
			helper.assertTrue(reachableFrom(level, box, origin.offset(ENTRANCE)).contains(origin.offset(COMPARTMENT_CHEST)), variant + ": the compartment should be reachable once open");
			helper.assertFalse(MechanistsVault.isProtected(level, vault, Blocks.LEVER.defaultBlockState()), variant + ": the machinery may be taken once both doors are open");
			helper.assertTrue(MechanistsVault.isProtected(level, vault, Blocks.REINFORCED_DEEPSLATE.defaultBlockState()), variant + ": the shell stays protected");
		} finally {
			DungeonStates.forget(level, vault.origin());

			for (BlockPos pos : positions(box)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}

		helper.succeed();
	}

	/**
	 * @return the gauge's neighbour on the control room's side: the open one with a floor tile of
	 *         the pattern beneath it
	 */
	private static BlockPos roomSide(ServerLevel level, BlockPos gauge) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos front = gauge.relative(side);
			BlockState tile = level.getBlockState(front.below(3));

			if (level.getBlockState(front).isAir() && (tile.is(Blocks.SEA_LANTERN) || tile.is(Blocks.SMOOTH_BASALT))) {
				return front;
			}
		}

		return gauge;
	}

	private static BlockPos supportOf(BlockPos attached, BlockState state) {
		AttachFace face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
		return face == AttachFace.FLOOR ? attached.below() : face == AttachFace.CEILING ? attached.above()
				: attached.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
	}

	private static void setLevers(ServerLevel level, Map<Integer, BlockPos> levers, boolean[] on) {
		for (int lever = 0; lever < on.length; lever++) {
			setLever(level, levers.get(lever), on[lever]);
		}
	}

	private static void setLever(ServerLevel level, BlockPos pos, boolean on) {
		level.setBlock(pos, level.getBlockState(pos).setValue(LeverBlock.POWERED, on), 3);
	}

	private static boolean lit(ServerLevel level, BlockPos gauge) {
		return level.getBlockState(gauge).getValue(CopperBulbBlock.LIT);
	}

	private static boolean anyLit(boolean[] gauges) {
		for (boolean gauge : gauges) {
			if (gauge) {
				return true;
			}
		}

		return false;
	}

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

	private static ResourceKey<LootTable> lootTableAt(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof ChestBlockEntity chest ? chest.getLootTable() : null;
	}
}
