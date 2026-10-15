package dev.wildfront.dungeon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.wildfront.Wildfront;
import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/**
 * The Mechanist's Vault, run on the server.
 *
 * <p>Five levers stand on a desk, each on a different copper block, and six gauges (copper bulbs)
 * in the wall, each on a different polished stone with a mark above it. Each lever flips a set of
 * gauges; which, the player finds out by pulling them, as every pull can be undone. When the
 * gauges match their marks, the obsidian door of the vault breaks open. With the test switch in
 * the shaft on and the gauges showing the pattern laid out in the floor, the crying obsidian of
 * the secret compartment does.
 *
 * <p>Which gauges burn is worked out from the levers, never read from the bulbs, so powering a
 * bulb some other way changes nothing. Levers and gauges are known by the block they stand on,
 * so the vault works however world generation turned it, and a lever broken off can be replaced.
 */
public final class MechanistsVault {
	public static final List<Block> LEVER_MOUNTS = blocks("waxed_copper_block", "waxed_exposed_copper", "waxed_weathered_copper",
			"waxed_oxidized_copper", "waxed_cut_copper");
	public static final List<Block> GAUGE_MARKERS = blocks("polished_andesite", "polished_diorite", "polished_granite",
			"polished_tuff", "polished_deepslate", "polished_blackstone");
	public static final Block TEST_MOUNT = blocks("waxed_chiseled_copper").get(0);
	public static final Block GAUGE = blocks("waxed_copper_bulb").get(0);

	public static final Block DOOR = Blocks.OBSIDIAN;
	public static final Block SECRET_DOOR = Blocks.CRYING_OBSIDIAN;
	public static final Block SHELL = Blocks.REINFORCED_DEEPSLATE;

	/** The marks above the gauges and the maintenance pattern in the floor. */
	public static final List<Block> CLUES = List.of(Blocks.GLOWSTONE, blocks("black_concrete").get(0), Blocks.SEA_LANTERN, Blocks.SMOOTH_BASALT);

	static final int MESSAGE_RADIUS = 32;

	/**
	 * One vault in the world.
	 *
	 * @param box     the blocks it occupies
	 * @param variant the template it was built from
	 */
	public record Vault(BoundingBox box, Identifier variant) {
		public BlockPos origin() {
			return new BlockPos(box.minX(), box.minY(), box.minZ());
		}

		public VaultCircuit circuit() {
			String path = variant.getPath();
			return VaultCircuit.VARIANTS.get(path.substring(path.lastIndexOf('/') + 1));
		}
	}

	/**
	 * What re-reading the levers did.
	 *
	 * @param gauges         which gauges burn now
	 * @param testSwitch     whether the test switch is on
	 * @param opened         whether the vault opened just now
	 * @param secretOpened   whether the compartment opened just now
	 */
	public record Update(boolean[] gauges, boolean testSwitch, boolean opened, boolean secretOpened) {
	}

	private record Pending(ResourceKey<Level> dimension, Vault vault) {
	}

	/** Vaults whose levers were used this tick; the levers have flipped by the end of it. */
	private static final Set<Pending> PENDING = new LinkedHashSet<>();

	private MechanistsVault() {
	}

	private static List<Block> blocks(String... ids) {
		List<Block> blocks = new ArrayList<>();

		for (String id : ids) {
			blocks.add(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)));
		}

		return List.copyOf(blocks);
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel serverLevel && !player.isSpectator()) {
				onUse(serverLevel, hit.getBlockPos());
			}

			// The lever flips as usual; the vault is worked out once it has.
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(MechanistsVault::updatePending);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> mayBreak(level, player, pos, state));
	}

	public static Optional<Vault> find(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(WildfrontStructures.MECHANISTS_VAULT));

		if (!start.isValid() || start.getPieces().isEmpty()
				|| !(start.getPieces().get(0) instanceof PoolElementStructurePiece piece)
				|| !(piece.getElement() instanceof SinglePoolElement element)) {
			return Optional.empty();
		}

		Vault vault = new Vault(piece.getBoundingBox(), element.getTemplateLocation());
		return vault.circuit() == null ? Optional.empty() : Optional.of(vault);
	}

	private static void onUse(ServerLevel level, BlockPos pos) {
		BlockState lever = level.getBlockState(pos);

		if (!(lever.getBlock() instanceof LeverBlock)) {
			return;
		}

		Block mount = level.getBlockState(supportOf(pos, lever)).getBlock();

		if (LEVER_MOUNTS.contains(mount) || mount == TEST_MOUNT) {
			find(level, pos).ifPresent(vault -> PENDING.add(new Pending(level.dimension(), vault)));
		}
	}

	private static void updatePending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		List<Pending> vaults = new ArrayList<>(PENDING);
		PENDING.clear();

		for (Pending pending : vaults) {
			ServerLevel level = server.getLevel(pending.dimension());

			if (level != null) {
				update(level, pending.vault());
			}
		}
	}

	static BlockPos supportOf(BlockPos attached, BlockState state) {
		return switch (state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)) {
			case FLOOR -> attached.below();
			case CEILING -> attached.above();
			case WALL -> attached.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
		};
	}

	/**
	 * Reads the levers, sets the gauges accordingly and opens what they open.
	 */
	public static Update update(ServerLevel level, Vault vault) {
		VaultCircuit circuit = vault.circuit();
		boolean[] levers = new boolean[VaultCircuit.LEVERS];
		boolean testSwitch = false;

		for (BlockPos pos : positions(vault.box())) {
			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof LeverBlock && state.getValue(LeverBlock.POWERED)) {
				Block mount = level.getBlockState(supportOf(pos, state)).getBlock();
				int lever = LEVER_MOUNTS.indexOf(mount);

				if (lever >= 0) {
					levers[lever] = true;
				}

				testSwitch |= mount == TEST_MOUNT;
			}
		}

		boolean[] gauges = circuit.gauges(levers);

		for (BlockPos pos : positions(vault.box())) {
			BlockState state = level.getBlockState(pos);
			int gauge = state.is(GAUGE) ? GAUGE_MARKERS.indexOf(level.getBlockState(pos.below()).getBlock()) : -1;

			if (gauge >= 0 && state.getValue(CopperBulbBlock.LIT) != gauges[gauge]) {
				level.setBlock(pos, state.setValue(CopperBulbBlock.LIT, gauges[gauge]), Block.UPDATE_CLIENTS);
			}
		}

		DungeonState state = DungeonStates.of(level, vault.origin());
		boolean opened = false;
		boolean secretOpened = false;

		if (!state.solved() && VaultCircuit.shows(gauges, circuit.target())) {
			state = state.asSolved();
			opened = true;
			breakAll(level, vault, DOOR);
			level.playSound(null, vault.box().getCenter(), SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 2.0F, 0.8F);
			tell(level, vault, Component.translatableWithFallback("dungeon.wildfront.vault.solved", "Every gauge reads true. The vault door gives way."));
			Wildfront.LOGGER.info("[Wildfront] Vault at {} was opened", vault.origin().toShortString());
		}

		if (!state.secretOpen() && testSwitch && VaultCircuit.shows(gauges, circuit.maintenance())) {
			state = state.withSecretOpen();
			secretOpened = true;
			breakAll(level, vault, SECRET_DOOR);
			level.playSound(null, vault.box().getCenter(), SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 2.0F, 1.4F);
			tell(level, vault, Component.translatableWithFallback("dungeon.wildfront.vault.secret", "Test passed. A hidden panel slides open."));
			Wildfront.LOGGER.info("[Wildfront] Compartment of the vault at {} was opened", vault.origin().toShortString());
		}

		if (opened || secretOpened) {
			DungeonStates.save(level, state);
		}

		return new Update(gauges, testSwitch, opened, secretOpened);
	}

	/**
	 * @return whether survival players are kept from breaking this block of the vault
	 */
	public static boolean isProtected(ServerLevel level, Vault vault, BlockState block) {
		if (block.is(SHELL)) {
			return true;
		}

		DungeonState state = DungeonStates.of(level, vault.origin());

		if (block.is(DOOR)) {
			return !state.solved();
		}

		if (block.is(SECRET_DOOR) || block.is(TEST_MOUNT)) {
			return !state.secretOpen();
		}

		// The machinery stays whole until both doors are open, as both need it.
		if (block.getBlock() instanceof LeverBlock || block.is(GAUGE) || LEVER_MOUNTS.contains(block.getBlock())
				|| GAUGE_MARKERS.contains(block.getBlock()) || CLUES.contains(block.getBlock())) {
			return !(state.solved() && state.secretOpen());
		}

		return false;
	}

	private static boolean mayBreak(Level level, Player player, BlockPos pos, BlockState block) {
		if (!(level instanceof ServerLevel serverLevel) || player.isCreative() || !mightBeProtected(block)) {
			return true;
		}

		Optional<Vault> vault = find(serverLevel, pos);

		if (vault.isEmpty() || !isProtected(serverLevel, vault.get(), block)) {
			return true;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback("dungeon.wildfront.vault.protected",
					"The mechanism is bolted fast.").withStyle(ChatFormatting.GOLD));
		}

		return false;
	}

	private static boolean mightBeProtected(BlockState block) {
		return block.is(SHELL) || block.is(DOOR) || block.is(SECRET_DOOR) || block.is(TEST_MOUNT) || block.is(GAUGE)
				|| block.getBlock() instanceof LeverBlock || LEVER_MOUNTS.contains(block.getBlock())
				|| GAUGE_MARKERS.contains(block.getBlock()) || CLUES.contains(block.getBlock());
	}

	private static Iterable<BlockPos> positions(BoundingBox box) {
		return BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	private static void breakAll(ServerLevel level, Vault vault, Block block) {
		for (BlockPos pos : positions(vault.box())) {
			if (level.getBlockState(pos).is(block)) {
				level.destroyBlock(pos.immutable(), false);
			}
		}
	}

	private static void tell(ServerLevel level, Vault vault, Component message) {
		BlockPos center = vault.box().getCenter();

		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().closerThan(center, MESSAGE_RADIUS)) {
				player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.GOLD));
			}
		}
	}
}
