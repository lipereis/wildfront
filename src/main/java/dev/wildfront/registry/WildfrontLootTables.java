package dev.wildfront.registry;

import dev.wildfront.Wildfront;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Keys of the loot tables shipped in {@code data/wildfront/loot_table}.
 */
public final class WildfrontLootTables {
	public static final ResourceKey<LootTable> FORGOTTEN_CACHE = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("discovery/forgotten_cache"));
	public static final ResourceKey<LootTable> ABANDONED_CAMP = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("structure/abandoned_camp"));

	public static final ResourceKey<LootTable> BANDIT_CAMP_SMALL = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("camp/bandit_small"));
	public static final ResourceKey<LootTable> CHIEF_STASH = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("camp/chief_stash"));

	/** Dropped by a camp chief in addition to what every guard drops. */
	public static final ResourceKey<LootTable> GUARD_CHIEF = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("entities/guard_chief"));

	private WildfrontLootTables() {
	}
}
