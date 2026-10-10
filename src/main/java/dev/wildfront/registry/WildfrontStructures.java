package dev.wildfront.registry;

import dev.wildfront.Wildfront;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Keys of the world generation data shipped in {@code data/wildfront}. The structures themselves
 * are entirely data-driven; these keys exist so code and tests can refer to them.
 */
public final class WildfrontStructures {
	public static final ResourceKey<Structure> ABANDONED_CAMP = ResourceKey.create(Registries.STRUCTURE, Wildfront.id("abandoned_camp"));
	public static final ResourceKey<StructureSet> ABANDONED_CAMPS = ResourceKey.create(Registries.STRUCTURE_SET, Wildfront.id("abandoned_camps"));
	public static final ResourceKey<StructureTemplatePool> ABANDONED_CAMP_START_POOL = ResourceKey.create(Registries.TEMPLATE_POOL, Wildfront.id("abandoned_camp/start"));
	public static final Identifier ABANDONED_CAMP_TEMPLATE = Wildfront.id("abandoned_camp/camp");
	public static final TagKey<Biome> HAS_ABANDONED_CAMP = TagKey.create(Registries.BIOME, Wildfront.id("has_structure/abandoned_camp"));

	private WildfrontStructures() {
	}
}
