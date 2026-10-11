package dev.wildfront.gametest.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.wildfront.registry.WildfrontStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

/**
 * Asks world generation where abandoned camps will be and which layout each will have, without
 * generating any terrain: it only computes the structure starts of the chunks the camp's placement
 * selects as candidates. That makes it cheap enough to look at hundreds of candidates.
 */
final class CampSurvey {
	/**
	 * @param start  position of the camp's piece, at its north-west corner before rotation
	 * @param layout id of the template world generation picked for it
	 */
	record Camp(BlockPos start, Identifier layout) {
	}

	/**
	 * @param candidates how many candidate chunks were examined
	 * @param camps      the candidates at which a camp will generate, nearest first
	 */
	record Result(int candidates, List<Camp> camps) {
		Map<Identifier, Integer> countByLayout() {
			Map<Identifier, Integer> counts = new LinkedHashMap<>();

			for (Identifier layout : WildfrontStructures.ABANDONED_CAMP_LAYOUTS) {
				counts.put(layout, 0);
			}

			for (Camp camp : camps) {
				counts.merge(camp.layout(), 1, Integer::sum);
			}

			return counts;
		}

		Camp firstWithLayout(Identifier layout) {
			return camps.stream().filter(camp -> camp.layout().equals(layout)).findFirst().orElse(null);
		}
	}

	private CampSurvey() {
	}

	/**
	 * Examines candidate chunks in growing rings around {@code center} until every layout has been
	 * seen and at least {@code enoughCamps} camps were found, or {@code maxCandidates} is reached.
	 */
	static Result run(ServerLevel level, BlockPos center, int enoughCamps, int maxCandidates) {
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WildfrontStructures.ABANDONED_CAMP).orElseThrow();
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) state.getPlacementsForStructure(holder).get(0);
		int spacing = placement.spacing();
		int centerRegionX = Math.floorDiv(center.getX() >> 4, spacing);
		int centerRegionZ = Math.floorDiv(center.getZ() >> 4, spacing);

		List<Camp> camps = new ArrayList<>();
		int candidates = 0;

		for (int ring = 0; candidates < maxCandidates; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					ChunkPos candidate = placement.getPotentialStructureChunk(state.getLevelSeed(), (centerRegionX + dx) * spacing, (centerRegionZ + dz) * spacing);
					candidates++;

					StructureStart start = level.getChunk(candidate.x(), candidate.z(), ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());

					if (start == null || !start.isValid()) {
						continue;
					}

					for (StructurePiece piece : start.getPieces()) {
						if (piece instanceof PoolElementStructurePiece poolPiece && poolPiece.getElement() instanceof SinglePoolElement element) {
							camps.add(new Camp(poolPiece.getPosition(), element.getTemplateLocation()));
						}
					}
				}
			}

			boolean everyLayoutSeen = WildfrontStructures.ABANDONED_CAMP_LAYOUTS.stream()
					.allMatch(layout -> camps.stream().anyMatch(camp -> camp.layout().equals(layout)));

			if (everyLayoutSeen && camps.size() >= enoughCamps) {
				break;
			}
		}

		return new Result(candidates, camps);
	}
}
