package dev.wildfront.registry;

import java.util.List;

import dev.wildfront.Wildfront;
import dev.wildfront.worldgen.FlatGroundJigsawStructure;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * World generation content. The structures themselves are defined by the data files in
 * {@code data/wildfront}; code only provides the structure type they use and keys to refer to them.
 */
public final class WildfrontStructures {
	/**
	 * Structure type {@code wildfront:flat_ground_jigsaw}; see {@link FlatGroundJigsawStructure}.
	 */
	public static final StructureType<FlatGroundJigsawStructure> FLAT_GROUND_JIGSAW = Registry.register(
			BuiltInRegistries.STRUCTURE_TYPE, Wildfront.id("flat_ground_jigsaw"), () -> FlatGroundJigsawStructure.CODEC);

	public static final ResourceKey<Structure> ABANDONED_CAMP = ResourceKey.create(Registries.STRUCTURE, Wildfront.id("abandoned_camp"));
	public static final ResourceKey<StructureSet> ABANDONED_CAMPS = ResourceKey.create(Registries.STRUCTURE_SET, Wildfront.id("abandoned_camps"));
	public static final ResourceKey<StructureTemplatePool> ABANDONED_CAMP_START_POOL = ResourceKey.create(Registries.TEMPLATE_POOL, Wildfront.id("abandoned_camp/start"));
	public static final Identifier ABANDONED_CAMP_TEMPLATE = Wildfront.id("abandoned_camp/camp");

	/**
	 * Every layout an abandoned camp can have; each is a template in the camp's start pool.
	 */
	public static final List<Identifier> ABANDONED_CAMP_LAYOUTS = List.of(
			ABANDONED_CAMP_TEMPLATE,
			Wildfront.id("abandoned_camp/tent"),
			Wildfront.id("abandoned_camp/cart"),
			Wildfront.id("abandoned_camp/ruin"));
	public static final ResourceKey<Structure> BANDIT_CAMP = ResourceKey.create(Registries.STRUCTURE, Wildfront.id("bandit_camp"));
	public static final ResourceKey<StructureSet> BANDIT_CAMPS = ResourceKey.create(Registries.STRUCTURE_SET, Wildfront.id("bandit_camps"));
	public static final ResourceKey<StructureTemplatePool> BANDIT_CAMP_START_POOL = ResourceKey.create(Registries.TEMPLATE_POOL, Wildfront.id("bandit_camp/start"));

	/**
	 * Every layout a bandit camp can have.
	 */
	public static final List<Identifier> BANDIT_CAMP_LAYOUTS = List.of(Wildfront.id("bandit_camp/small_1"));

	public static final TagKey<Biome> HAS_ABANDONED_CAMP = TagKey.create(Registries.BIOME, Wildfront.id("has_structure/abandoned_camp"));

	private WildfrontStructures() {
	}

	public static void init() {
		// Loads this class so the structure type is registered during mod initialization, before
		// the game reads the structure data that refers to it.
	}
}
