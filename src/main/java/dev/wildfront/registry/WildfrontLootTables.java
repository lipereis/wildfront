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

	private WildfrontLootTables() {
	}
}
