package dev.wildfront.worldgen;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.wildfront.registry.WildfrontStructures;

import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * A jigsaw structure that only generates where the ground is dry and roughly level.
 *
 * <p>A plain jigsaw structure is placed wherever its spacing says, and the terrain is then cut and
 * filled around it. For a small surface structure that produces a squared notch in a hillside, or
 * a platform in a pond. This type takes every setting of {@code minecraft:jigsaw} plus two of its
 * own and simply does not generate at a spot that fails the check:
 *
 * <ul>
 * <li>{@code check_radius}: half the side of the square, centred on the structure's start, that
 * is sampled at nine points;</li>
 * <li>{@code max_height_difference}: the largest allowed difference between the highest and the
 * lowest sampled surface height.</li>
 * </ul>
 *
 * <p>The check costs eighteen height lookups, and only for the few chunks the structure's
 * placement selects as candidates.
 */
public final class FlatGroundJigsawStructure extends Structure {
	public static final MapCodec<FlatGroundJigsawStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			JigsawStructure.CODEC.forGetter(structure -> structure.jigsaw),
			Codec.intRange(1, 32).fieldOf("check_radius").forGetter(structure -> structure.checkRadius),
			Codec.intRange(0, 64).fieldOf("max_height_difference").forGetter(structure -> structure.maxHeightDifference)
	).apply(instance, FlatGroundJigsawStructure::new));

	private final JigsawStructure jigsaw;
	private final int checkRadius;
	private final int maxHeightDifference;

	public FlatGroundJigsawStructure(JigsawStructure jigsaw, int checkRadius, int maxHeightDifference) {
		super(new StructureSettings(jigsaw.biomes(), jigsaw.spawnOverrides(), jigsaw.step(), jigsaw.terrainAdaptation()));
		this.jigsaw = jigsaw;
		this.checkRadius = checkRadius;
		this.maxHeightDifference = maxHeightDifference;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		// Jigsaw structures start at the north-west corner of their chunk.
		if (!isDryAndLevel(context, context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(), checkRadius, maxHeightDifference)) {
			return Optional.empty();
		}

		return jigsaw.findGenerationPoint(context);
	}

	/**
	 * Asks the terrain generator, not the world, so it works before any chunk exists.
	 */
	public static boolean isDryAndLevel(GenerationContext context, int x, int z, int checkRadius, int maxHeightDifference) {
		int[][] offsets = HeightSpread.sampleOffsets(checkRadius);
		int[] surface = new int[offsets.length];
		int[] floor = new int[offsets.length];

		for (int i = 0; i < offsets.length; i++) {
			int sampleX = x + offsets[i][0];
			int sampleZ = z + offsets[i][1];
			surface[i] = context.chunkGenerator().getFirstOccupiedHeight(sampleX, sampleZ, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			floor[i] = context.chunkGenerator().getFirstOccupiedHeight(sampleX, sampleZ, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
		}

		return !HeightSpread.hasWater(surface, floor) && HeightSpread.isLevel(surface, maxHeightDifference);
	}

	@Override
	public StructureType<?> type() {
		return WildfrontStructures.FLAT_GROUND_JIGSAW;
	}
}
