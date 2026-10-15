package dev.wildfront.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.wildfront.Wildfront;
import dev.wildfront.registry.WildfrontAttachments;
import dev.wildfront.registry.WildfrontStructures;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/**
 * The Forgotten Temple's puzzle, run on the server.
 *
 * <p>Four pedestals carry a glyph each, with a button. Murals in the entrance hall show the same
 * glyphs with one to four candles: the order in which they must be woken. Pressing the right
 * button lights the candle on its pedestal; a wrong one puts them all out and the order starts
 * over. When all four are awake, the crying obsidian seal of the sanctum crumbles. A fifth glyph,
 * hidden behind vines, opens the obsidian seal of the reliquary.
 *
 * <p>Everything is read from the blocks: a glyph is known by its kind of block, never by its
 * position, so the temple works however world generation turned it. Which variant of the temple
 * generated, and so which order is right, is read from the structure's template.
 *
 * <p>Until a seal is open, survival players cannot break it, the reinforced deepslate around the
 * chambers, the glyphs or the candles; explosions and pistons cannot move those blocks anyway.
 */
public final class ForgottenTemple {
	public static final Block GLYPH_A = Blocks.CHISELED_STONE_BRICKS;
	public static final Block GLYPH_B = Blocks.CHISELED_TUFF_BRICKS;
	public static final Block GLYPH_C = Blocks.CHISELED_DEEPSLATE;
	public static final Block GLYPH_D = Blocks.CHISELED_RED_SANDSTONE;
	public static final List<Block> GLYPHS = List.of(GLYPH_A, GLYPH_B, GLYPH_C, GLYPH_D);
	public static final Block SECRET_GLYPH = Blocks.CHISELED_POLISHED_BLACKSTONE;

	public static final Block SEAL = Blocks.CRYING_OBSIDIAN;
	public static final Block SECRET_SEAL = Blocks.OBSIDIAN;
	public static final Block SHELL = Blocks.REINFORCED_DEEPSLATE;

	/**
	 * The right order for each variant. It must match the candles of the variant's murals, which
	 * the game tests check by reading them from the placed template.
	 */
	public static final Map<Identifier, List<Block>> SOLUTIONS = Map.of(
			Wildfront.id("forgotten_temple/variant_1"), List.of(GLYPH_B, GLYPH_D, GLYPH_A, GLYPH_C),
			Wildfront.id("forgotten_temple/variant_2"), List.of(GLYPH_D, GLYPH_B, GLYPH_C, GLYPH_A),
			Wildfront.id("forgotten_temple/variant_3"), List.of(GLYPH_A, GLYPH_C, GLYPH_D, GLYPH_B));

	/** Players this close to the temple are told when a seal opens. */
	static final int MESSAGE_RADIUS = 32;

	/**
	 * One temple in the world.
	 *
	 * @param box     the blocks it occupies
	 * @param variant the template it was built from
	 */
	public record Temple(BoundingBox box, Identifier variant) {
		public BlockPos origin() {
			return new BlockPos(box.minX(), box.minY(), box.minZ());
		}

		public List<Block> solution() {
			return SOLUTIONS.getOrDefault(variant, List.of());
		}
	}

	private ForgottenTemple() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel serverLevel && !player.isSpectator() && !player.isSecondaryUseActive()) {
				onUse(serverLevel, player, hit.getBlockPos());
			}

			// Never stops the click: the button is pressed as usual, which is part of the feedback.
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> mayBreak(level, player, pos, state));
	}

	/**
	 * @return the temple that has a block at {@code pos}, if any
	 */
	public static Optional<Temple> find(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(WildfrontStructures.FORGOTTEN_TEMPLE));

		if (!start.isValid() || start.getPieces().isEmpty()
				|| !(start.getPieces().get(0) instanceof PoolElementStructurePiece piece)
				|| !(piece.getElement() instanceof SinglePoolElement element)) {
			return Optional.empty();
		}

		return Optional.of(new Temple(piece.getBoundingBox(), element.getTemplateLocation()));
	}

	private static void onUse(ServerLevel level, Player player, BlockPos pos) {
		BlockState button = level.getBlockState(pos);

		// A button that is still down does nothing when clicked, so neither does the temple.
		if (!(button.getBlock() instanceof ButtonBlock) || button.getValue(ButtonBlock.POWERED)) {
			return;
		}

		BlockPos support = supportOf(pos, button);
		Block glyph = level.getBlockState(support).getBlock();

		if (GLYPHS.contains(glyph) || glyph == SECRET_GLYPH) {
			find(level, support).ifPresent(temple -> press(level, temple, support, glyph));
		}
	}

	/**
	 * @return the block a button is fixed to
	 */
	static BlockPos supportOf(BlockPos button, BlockState state) {
		return switch (state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)) {
			case FLOOR -> button.below();
			case CEILING -> button.above();
			case WALL -> button.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
		};
	}

	/**
	 * A glyph's button was pressed in the temple.
	 *
	 * @param glyphPos where the glyph is
	 * @return what the press did to the puzzle
	 */
	public static GlyphSequence.Outcome press(ServerLevel level, Temple temple, BlockPos glyphPos, Block glyph) {
		DungeonState state = stateOf(level, temple);

		if (glyph == SECRET_GLYPH) {
			if (!state.secretOpen()) {
				save(level, state.withSecretOpen());
				breakAll(level, temple, SECRET_SEAL);
				tell(level, temple, Component.translatableWithFallback("dungeon.wildfront.temple.secret", "Something gives way behind the vines."));
				Wildfront.LOGGER.info("[Wildfront] Reliquary of the temple at {} was opened", temple.origin().toShortString());
			}

			return GlyphSequence.Outcome.IGNORED;
		}

		GlyphSequence.Step step = GlyphSequence.press(temple.solution(), state.progress(), state.solved(), glyph);

		switch (step.outcome()) {
			case ADVANCED -> {
				save(level, state.withProgress(step.progress()));
				light(level, glyphPos.above(), true);
				level.playSound(null, glyphPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 0.6F + 0.2F * step.progress());
			}
			case SOLVED -> {
				save(level, state.withProgress(step.progress()).asSolved());
				light(level, glyphPos.above(), true);
				breakAll(level, temple, SEAL);
				level.playSound(null, glyphPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 0.8F);
				tell(level, temple, Component.translatableWithFallback("dungeon.wildfront.temple.solved", "The four are awake. The sanctum's seal crumbles."));
				Wildfront.LOGGER.info("[Wildfront] Temple at {} was solved", temple.origin().toShortString());
			}
			case WRONG -> {
				save(level, state.withProgress(0));
				putOutPedestals(level, temple);
				level.playSound(null, glyphPos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 2.0F, 0.6F);
				tell(level, temple, Component.translatableWithFallback("dungeon.wildfront.temple.wrong", "The glyphs fall dark."));
			}
			case IGNORED -> {
			}
		}

		return step.outcome();
	}

	/**
	 * @return whether survival players are kept from breaking this block of the temple
	 */
	public static boolean isProtected(ServerLevel level, Temple temple, BlockState block) {
		if (block.is(SHELL)) {
			return true;
		}

		DungeonState state = stateOf(level, temple);

		if (block.is(SEAL) || GLYPHS.contains(block.getBlock()) || block.getBlock() instanceof CandleBlock) {
			return !state.solved();
		}

		if (block.is(SECRET_SEAL) || block.is(SECRET_GLYPH)) {
			return !state.secretOpen();
		}

		return false;
	}

	private static boolean mayBreak(Level level, Player player, BlockPos pos, BlockState block) {
		if (!(level instanceof ServerLevel serverLevel) || player.isCreative() || !mightBeProtected(block)) {
			return true;
		}

		Optional<Temple> temple = find(serverLevel, pos);

		if (temple.isEmpty() || !isProtected(serverLevel, temple.get(), block)) {
			return true;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatableWithFallback("dungeon.wildfront.temple.protected",
					"An old power holds this in place.").withStyle(ChatFormatting.DARK_AQUA));
		}

		return false;
	}

	/** A quick check by kind of block, before looking for a temple. */
	private static boolean mightBeProtected(BlockState block) {
		return block.is(SHELL) || block.is(SEAL) || block.is(SECRET_SEAL) || block.is(SECRET_GLYPH)
				|| GLYPHS.contains(block.getBlock()) || block.getBlock() instanceof CandleBlock;
	}

	public static List<DungeonState> states(ServerLevel level) {
		return level.getAttachedOrElse(WildfrontAttachments.DUNGEONS, List.of());
	}

	public static DungeonState stateOf(ServerLevel level, Temple temple) {
		BlockPos origin = temple.origin();
		return states(level).stream().filter(state -> state.origin().equals(origin)).findFirst().orElseGet(() -> DungeonState.untouched(origin));
	}

	private static void save(ServerLevel level, DungeonState state) {
		List<DungeonState> states = new ArrayList<>(states(level));
		states.removeIf(other -> other.origin().equals(state.origin()));
		states.add(state);
		level.setAttached(WildfrontAttachments.DUNGEONS, List.copyOf(states));
	}

	/**
	 * Forgets a temple's state. Its seals stay as they are. Used by tests.
	 */
	public static void forget(ServerLevel level, Temple temple) {
		List<DungeonState> states = new ArrayList<>(states(level));

		if (states.removeIf(state -> state.origin().equals(temple.origin()))) {
			level.setAttached(WildfrontAttachments.DUNGEONS, List.copyOf(states));
		}
	}

	private static void light(ServerLevel level, BlockPos pos, boolean lit) {
		BlockState candle = level.getBlockState(pos);

		if (candle.getBlock() instanceof CandleBlock && candle.getValue(CandleBlock.LIT) != lit) {
			level.setBlock(pos, candle.setValue(CandleBlock.LIT, lit), Block.UPDATE_ALL);
		}
	}

	/** Puts out the candles standing on the pedestal glyphs; the murals' candles stay lit. */
	private static void putOutPedestals(ServerLevel level, Temple temple) {
		for (BlockPos pos : BlockPos.betweenClosed(temple.box().minX(), temple.box().minY(), temple.box().minZ(),
				temple.box().maxX(), temple.box().maxY(), temple.box().maxZ())) {
			if (GLYPHS.contains(level.getBlockState(pos.below()).getBlock())) {
				light(level, pos, false);
			}
		}
	}

	private static void breakAll(ServerLevel level, Temple temple, Block block) {
		for (BlockPos pos : BlockPos.betweenClosed(temple.box().minX(), temple.box().minY(), temple.box().minZ(),
				temple.box().maxX(), temple.box().maxY(), temple.box().maxZ())) {
			if (level.getBlockState(pos).is(block)) {
				level.destroyBlock(pos.immutable(), false);
			}
		}
	}

	private static void tell(ServerLevel level, Temple temple, Component message) {
		BlockPos center = temple.box().getCenter();

		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().closerThan(center, MESSAGE_RADIUS)) {
				player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.DARK_AQUA));
			}
		}
	}
}
