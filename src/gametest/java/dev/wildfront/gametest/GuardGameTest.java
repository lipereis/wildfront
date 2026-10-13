package dev.wildfront.gametest;

import java.util.List;

import dev.wildfront.entity.GuardEntity;
import dev.wildfront.registry.WildfrontEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * The guard entity on a real server: what it is, whom it attacks, and what it leaves behind.
 */
public class GuardGameTest {
	private static GuardEntity spawnGuard(GameTestHelper helper, int x, int y, int z) {
		ServerLevel level = helper.getLevel();

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
		}

		BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
		GuardEntity guard = WildfrontEntities.GUARD.create(level, EntitySpawnReason.COMMAND);
		helper.assertTrue(guard != null, "a guard should be creatable");
		guard.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
		guard.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
		guard.setPersistenceRequired();
		helper.assertTrue(level.addFreshEntity(guard), "a guard should be addable to the world");
		return guard;
	}

	@GameTest
	public void guardIsARegisteredArmedMonster(GameTestHelper helper) {
		GameTestSupport.buildFloor(helper);

		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(WildfrontEntities.GUARD).toString().equals("wildfront:guard"), "the guard should be registered as wildfront:guard");
		helper.assertTrue(WildfrontEntities.GUARD.getCategory() == MobCategory.MONSTER, "a guard should count as a monster");

		GuardEntity guard = spawnGuard(helper, 3, 1, 3);

		try {
			helper.assertTrue(guard.getMaxHealth() == (float) GuardEntity.MAX_HEALTH && guard.getHealth() == guard.getMaxHealth(),
					"a guard should have " + GuardEntity.MAX_HEALTH + " health but has " + guard.getHealth() + " of " + guard.getMaxHealth());
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "a guard should carry an iron sword but holds " + guard.getMainHandItem());
			helper.assertTrue(guard.getAttributeBaseValue(Attributes.FOLLOW_RANGE) == GuardEntity.SIGHT, "a guard should see " + GuardEntity.SIGHT + " blocks far before the game adds its random bonus");
			helper.assertTrue(guard.getType().getDefaultLootTable().isPresent(), "a guard should have a loot table");
			LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(guard.getType().getDefaultLootTable().orElseThrow());
			helper.assertTrue(table != LootTable.EMPTY, "the guard's loot table should be loaded from the mod's data");
		} catch (RuntimeException e) {
			guard.discard();
			throw e;
		}

		// The game applies what a held item adds to the attributes on the entity's next tick.
		helper.runAfterDelay(5, () -> {
			double damage = guard.getAttributeValue(Attributes.ATTACK_DAMAGE);
			guard.discard();
			helper.assertTrue(damage == 6.0, "a guard with its sword should deal 6 damage, but deals " + damage);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void guardAttacksASurvivalPlayerAndIgnoresACreativeOne(GameTestHelper helper) {
		GameTestSupport.buildFloor(helper);
		GuardEntity guard = spawnGuard(helper, 1, 1, 1);
		ServerPlayer creative = GameTestSupport.addPlayer(helper, GameType.CREATIVE, 2, 1, 3);
		ServerPlayer survivor = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 6, 1, 6);
		float healthBefore = survivor.getHealth();

		helper.onEachTick(() -> {
			if (guard.getTarget() == creative) {
				GameTestSupport.remove(helper, creative, survivor);
				guard.discard();
				helper.fail("a guard must not attack a player in creative mode");
			}
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(guard.getTarget() == survivor, "the guard should go after the survival player, but its target is " + guard.getTarget());
			helper.assertTrue(survivor.getHealth() < healthBefore, "the guard should hurt the survival player, who still has " + survivor.getHealth() + " health");
			helper.assertTrue(creative.getHealth() == creative.getMaxHealth(), "the creative player should be unharmed");
			GameTestSupport.remove(helper, creative, survivor);
			guard.discard();
		});
	}

	@GameTest
	public void guardsKilledByAPlayerDropLoot(GameTestHelper helper) {
		GameTestSupport.buildFloor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 6, 1, 6);
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(10);

		try {
			for (int i = 0; i < 40; i++) {
				GuardEntity guard = spawnGuard(helper, 3, 1, 3);
				guard.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
				guard.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
				helper.assertFalse(guard.isAlive(), "a guard should die of 1000 damage");
			}

			List<Item> dropped = level.getEntitiesOfClass(ItemEntity.class, area).stream().map(item -> item.getItem().getItem()).distinct().toList();
			helper.assertTrue(dropped.contains(Items.EMERALD), "guards killed by a player should sometimes drop an emerald, but 40 of them dropped " + dropped);
			helper.assertTrue(dropped.contains(Items.BREAD) || dropped.contains(Items.ARROW), "guards should drop bread or arrows, but 40 of them dropped " + dropped);
			helper.assertTrue(dropped.stream().allMatch(item -> item == Items.EMERALD || item == Items.BREAD || item == Items.ARROW),
					"guards should drop nothing but their loot table's items, but dropped " + dropped);
		} finally {
			level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
			GameTestSupport.remove(helper, player);
			helper.killAllEntities();
		}

		helper.succeed();
	}
}
