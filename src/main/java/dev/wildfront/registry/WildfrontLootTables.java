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

	public static final ResourceKey<LootTable> TEMPLE_SANCTUM = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("dungeon/temple_sanctum"));
	public static final ResourceKey<LootTable> VAULT_MAIN = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("dungeon/vault_main"));
	public static final ResourceKey<LootTable> VAULT_COMPARTMENT = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("dungeon/vault_compartment"));
	public static final ResourceKey<LootTable> TEMPLE_RELIQUARY = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("dungeon/temple_reliquary"));

	/** Dropped by a camp chief in addition to what every guard drops. */
	public static final ResourceKey<LootTable> GUARD_CHIEF = ResourceKey.create(Registries.LOOT_TABLE, Wildfront.id("entities/guard_chief"));

	private WildfrontLootTables() {
	}
}
