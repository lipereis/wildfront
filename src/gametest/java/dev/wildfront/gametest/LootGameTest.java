package dev.wildfront.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.wildfront.registry.WildfrontLootTables;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Checks that the loot tables do what they say, not merely that they produce something. A loot
 * table written with keys the game no longer knows still loads and still yields items; it just
 * silently loses its stack sizes, enchantments and wear.
 */
public class LootGameTest {
	private static final BlockPos CHEST = new BlockPos(1, 1, 1);
	private static final int DRAWS = 200;

	/**
	 * The content of one generated chest. The game spreads a stack over several slots, so amounts
	 * are compared per chest, not per slot.
	 */
	private record Content(Map<Item, Integer> totals, List<ItemStack> stacks) {
		int total(Item item) {
			return totals.getOrDefault(item, 0);
		}
	}

	private static List<Content> draw(GameTestHelper helper, ResourceKey<LootTable> table) {
		List<Content> contents = new ArrayList<>();

		for (int seed = 1; seed <= DRAWS; seed++) {
			helper.setBlock(CHEST, Blocks.CHEST);
			ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
			chest.setLootTable(table, seed);
			chest.unpackLootTable(null);

			Map<Item, Integer> totals = new HashMap<>();
			List<ItemStack> stacks = new ArrayList<>();

			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				ItemStack stack = chest.getItem(slot);

				if (!stack.isEmpty()) {
					totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
					stacks.add(stack.copy());
				}
			}

			contents.add(new Content(totals, stacks));
			chest.clearContent();
		}

		helper.setBlock(CHEST, Blocks.AIR);
		return contents;
	}

	private static int chestsWith(List<Content> contents, Item item) {
		return (int) contents.stream().filter(content -> content.total(item) > 0).count();
	}

	/**
	 * Fails if any chest holds some of the item but fewer than one roll of the loot table gives.
	 */
	private static void assertAtLeastWhenPresent(GameTestHelper helper, List<Content> contents, Item item, int minimum) {
		for (Content content : contents) {
			int total = content.total(item);
			helper.assertTrue(total == 0 || total >= minimum, "a chest with " + item + " should hold at least " + minimum + ", but one held " + total);
		}
	}

	@GameTest
	public void forgottenCacheLootHasItsStackSizesAndEnchantments(GameTestHelper helper) {
		List<Content> caches = draw(helper, WildfrontLootTables.FORGOTTEN_CACHE);

		assertAtLeastWhenPresent(helper, caches, Items.IRON_INGOT, 2);
		assertAtLeastWhenPresent(helper, caches, Items.ARROW, 4);
		assertAtLeastWhenPresent(helper, caches, Items.BREAD, 2);
		assertAtLeastWhenPresent(helper, caches, Items.EXPERIENCE_BOTTLE, 2);

		helper.assertTrue(chestsWith(caches, Items.IRON_INGOT) > 0, "iron ingots should turn up in " + DRAWS + " caches");
		helper.assertTrue(chestsWith(caches, Items.DIAMOND) > 0, "diamonds should turn up in " + DRAWS + " caches");
		helper.assertTrue(caches.stream().allMatch(cache -> cache.total(Items.DIAMOND) <= 2), "a cache should hold at most 2 diamonds");
		helper.assertTrue(chestsWith(caches, Items.BOOK) == 0, "the cache should contain enchanted books, but plain books turned up in "
				+ chestsWith(caches, Items.BOOK) + " caches");
		helper.assertTrue(chestsWith(caches, Items.ENCHANTED_BOOK) > 0, "an enchanted book should turn up in " + DRAWS + " caches");
		helper.succeed();
	}

	@GameTest
	public void abandonedCampLootHasItsStackSizesAndWear(GameTestHelper helper) {
		List<Content> chests = draw(helper, WildfrontLootTables.ABANDONED_CAMP);

		assertAtLeastWhenPresent(helper, chests, Items.COAL, 2);
		assertAtLeastWhenPresent(helper, chests, Items.TORCH, 2);
		assertAtLeastWhenPresent(helper, chests, Items.ARROW, 2);
		assertAtLeastWhenPresent(helper, chests, Items.IRON_NUGGET, 2);

		int worn = 0;

		for (Content chest : chests) {
			for (ItemStack stack : chest.stacks()) {
				Item item = stack.getItem();

				if (item == Items.IRON_PICKAXE || item == Items.IRON_AXE || item == Items.LEATHER_BOOTS) {
					helper.assertTrue(stack.isDamaged(), "tools and boots left in a camp should be worn, but a " + item + " was as new");
					worn++;
				}
			}
		}

		helper.assertTrue(chestsWith(chests, Items.COAL) > 0, "coal should turn up in " + DRAWS + " camp chests");
		helper.assertTrue(worn > 0, "a worn tool or boots should turn up in " + DRAWS + " camp chests");
		helper.assertTrue(chestsWith(chests, Items.BOOK) == 0, "the camp chest should contain enchanted books, but plain books turned up in "
				+ chestsWith(chests, Items.BOOK) + " chests");
		helper.assertTrue(chestsWith(chests, Items.ENCHANTED_BOOK) > 0, "an enchanted book should turn up in " + DRAWS + " camp chests");
		helper.succeed();
	}

	@GameTest
	public void templeSanctumAndReliquaryLoot(GameTestHelper helper) {
		List<Content> sanctum = draw(helper, WildfrontLootTables.TEMPLE_SANCTUM);
		helper.assertTrue(sanctum.stream().allMatch(chest -> chest.total(Items.DIAMOND) <= 2), "a sanctum chest should hold at most 2 diamonds");
		assertAtLeastWhenPresent(helper, sanctum, Items.IRON_INGOT, 2);
		assertAtLeastWhenPresent(helper, sanctum, Items.LAPIS_LAZULI, 4);
		helper.assertTrue(chestsWith(sanctum, Items.ENCHANTED_BOOK) > 0, "an enchanted book should turn up in " + DRAWS + " sanctum chests");
		helper.assertTrue(chestsWith(sanctum, Items.BOOK) == 0, "plain books turned up in " + chestsWith(sanctum, Items.BOOK) + " sanctum chests");
		helper.assertTrue(sanctum.stream().allMatch(chest -> chest.stacks().size() >= 4), "every sanctum chest should hold at least four stacks");

		List<Content> reliquary = draw(helper, WildfrontLootTables.TEMPLE_RELIQUARY);
		helper.assertTrue(reliquary.stream().allMatch(chest -> chest.total(Items.EMERALD) >= 4 && chest.total(Items.EMERALD) <= 8),
				"every reliquary should hold 4 to 8 emeralds");
		helper.assertTrue(chestsWith(reliquary, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE) > 0, "the wild armour trim should turn up in " + DRAWS + " reliquaries");
		helper.assertTrue(chestsWith(reliquary, Items.ENCHANTED_BOOK) > 0 && chestsWith(reliquary, Items.BOOK) == 0, "reliquary books should be enchanted");
		helper.succeed();
	}

	@GameTest
	public void chiefStashIsRich(GameTestHelper helper) {
		List<Content> stashes = draw(helper, WildfrontLootTables.CHIEF_STASH);

		for (Content stash : stashes) {
			helper.assertTrue(stash.total(Items.DIAMOND) >= 2 && stash.total(Items.DIAMOND) <= 4, "every stash should hold 2 to 4 diamonds, but one held " + stash.total(Items.DIAMOND));
			helper.assertTrue(stash.total(Items.EMERALD) >= 6, "every stash should hold at least 6 emeralds, but one held " + stash.total(Items.EMERALD));
		}

		helper.assertTrue(chestsWith(stashes, Items.ENCHANTED_BOOK) > 0, "an enchanted book should turn up in " + DRAWS + " stashes");
		helper.assertTrue(chestsWith(stashes, Items.BOOK) == 0, "plain books turned up in " + chestsWith(stashes, Items.BOOK) + " stashes");
		helper.succeed();
	}

	@GameTest
	public void banditCampLootHasItsStackSizesAndWear(GameTestHelper helper) {
		List<Content> chests = draw(helper, WildfrontLootTables.BANDIT_CAMP_SMALL);

		assertAtLeastWhenPresent(helper, chests, Items.ARROW, 6);
		assertAtLeastWhenPresent(helper, chests, Items.IRON_INGOT, 2);
		assertAtLeastWhenPresent(helper, chests, Items.EMERALD, 2);
		assertAtLeastWhenPresent(helper, chests, Items.COAL, 3);

		int worn = 0;

		for (Content chest : chests) {
			for (ItemStack stack : chest.stacks()) {
				Item item = stack.getItem();

				if (item == Items.CROSSBOW || item == Items.IRON_SWORD || item == Items.SHIELD || item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE) {
					helper.assertTrue(stack.isDamaged(), "gear taken from bandits should be worn, but a " + item + " was as new");
					worn++;
				}
			}
		}

		helper.assertTrue(worn == DRAWS, "every bandit chest should hold exactly one piece of gear, but " + worn + " turned up in " + DRAWS + " chests");
		helper.assertTrue(chestsWith(chests, Items.ARROW) > 0, "arrows should turn up in " + DRAWS + " bandit chests");
		helper.assertTrue(chestsWith(chests, Items.DIAMOND) > 0, "diamonds should turn up in " + DRAWS + " bandit chests");
		helper.assertTrue(chests.stream().allMatch(chest -> chest.total(Items.DIAMOND) <= 2), "a bandit chest should hold at most 2 diamonds");
		helper.assertTrue(chestsWith(chests, Items.BOOK) == 0, "plain books turned up in " + chestsWith(chests, Items.BOOK) + " bandit chests");
		helper.assertTrue(chestsWith(chests, Items.ENCHANTED_BOOK) > 0, "an enchanted book should turn up in " + DRAWS + " bandit chests");
		helper.succeed();
	}
}
