package dev.wildfront.gametest;

import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.wildfront.entity.GuardEntity;
import dev.wildfront.registry.WildfrontEntities;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
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
			// A war cry in a test running next to this one may have made it stronger.
			guard.removeAllEffects();
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

	@GameTest(maxTicks = 400)
	public void guardWithABowShootsFromADistance(GameTestHelper helper) {
		GameTestSupport.buildFloor(helper);
		ServerLevel level = helper.getLevel();
		GuardEntity guard = spawnGuard(helper, 1, 1, 1);
		guard.equip(GuardEntity.Role.RANGED);
		// It must not win by walking up to the player: an archer without legs can only shoot.
		guard.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		ServerPlayer survivor = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 6, 1, 6);
		float healthBefore = survivor.getHealth();
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(12);

		helper.assertTrue(guard.isArcher(), "a guard equipped for the ranged role should hold a bow but holds " + guard.getMainHandItem());

		helper.succeedWhen(() -> {
			helper.assertTrue(survivor.getHealth() < healthBefore, "the archer should hit the survival player, who still has " + survivor.getHealth() + " health");
			helper.assertTrue(guard.distanceTo(survivor) > 4.0F, "the archer should have hit from a distance, but is " + guard.distanceTo(survivor) + " blocks away");
			level.getEntitiesOfClass(AbstractArrow.class, area).forEach(AbstractArrow::discard);
			GameTestSupport.remove(helper, survivor);
			guard.discard();
		});
	}

	@GameTest(maxTicks = 400)
	public void summonedGuardsAreSwordsmenUnlessGivenABow(GameTestHelper helper) throws CommandSyntaxException {
		GameTestSupport.buildFloor(helper);
		ServerLevel level = helper.getLevel();

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
		}

		BlockPos swordPos = helper.absolutePos(new BlockPos(1, 1, 6));
		BlockPos bowPos = helper.absolutePos(new BlockPos(1, 1, 1));
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(12);
		var console = level.getServer().createCommandSourceStack().withSuppressedOutput();
		var dispatcher = level.getServer().getCommands().getDispatcher();

		dispatcher.execute("summon wildfront:guard " + swordPos.getX() + " " + swordPos.getY() + " " + swordPos.getZ() + " {NoAI:1b,Tags:[\"wf-summon-sword\"]}", console);
		dispatcher.execute("summon wildfront:guard " + bowPos.getX() + " " + bowPos.getY() + " " + bowPos.getZ()
				+ " {Tags:[\"wf-summon-bow\"],equipment:{mainhand:{id:\"minecraft:bow\"}}}", console);

		List<GuardEntity> summoned = level.getEntitiesOfClass(GuardEntity.class, area, guard -> guard.entityTags().contains("wf-summon-bow"));
		helper.assertTrue(summoned.size() == 1, "the summon command with a bow should create one guard, but created " + summoned.size());
		GuardEntity archer = summoned.get(0);
		helper.assertTrue(archer.isArcher(), "a guard summoned with a bow should be an archer but holds " + archer.getMainHandItem());
		// Rooted to the spot, so that only its arrows can reach the player.
		archer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);

		ServerPlayer survivor = GameTestSupport.addPlayer(helper, GameType.SURVIVAL, 6, 1, 6);
		float healthBefore = survivor.getHealth();

		helper.succeedWhen(() -> {
			helper.assertTrue(survivor.getHealth() < healthBefore, "the summoned archer should shoot the survival player, who still has " + survivor.getHealth() + " health");
			helper.assertTrue(archer.distanceTo(survivor) > 4.0F, "the summoned archer should have hit from a distance");
			level.getEntitiesOfClass(AbstractArrow.class, area).forEach(AbstractArrow::discard);
			GameTestSupport.remove(helper, survivor);
			level.getEntitiesOfClass(GuardEntity.class, area, guard -> guard.entityTags().contains("wf-summon-bow") || guard.entityTags().contains("wf-summon-sword"))
					.forEach(GuardEntity::discard);
		});
	}

	@GameTest
	public void chiefCallsAWarCryAtHalfHealthAndDropsItsReward(GameTestHelper helper) {
		GameTestSupport.buildFloor(helper);
		ServerLevel level = helper.getLevel();
		GuardEntity chief = spawnGuard(helper, 3, 1, 3);
		chief.makeChief();
		GuardEntity guard = spawnGuard(helper, 5, 1, 3);
		guard.setNoAi(true);
		chief.setNoAi(true);
		ServerPlayer player = GameTestSupport.addPlayer(helper, GameType.CREATIVE, 7, 1, 7);
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(10);

		try {
			helper.assertTrue(chief.isChief() && chief.getHealth() == (float) GuardEntity.CHIEF_HEALTH, "the guard should now be a chief with full health");
			// The sword's damage is applied on the chief's next tick, which this test does not wait for.
			helper.assertTrue(chief.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == GuardEntity.CHIEF_ATTACK_DAMAGE, "a chief should hit harder bare-handed than a guard");
			chief.hurtServer(level, level.damageSources().magic(), 20.0F);
			helper.assertFalse(chief.hasCalledWarCry(), "the chief should not call its war cry above half health");

			// Right after a hit only damage beyond that hit counts: 45 now takes another 25.
			chief.hurtServer(level, level.damageSources().magic(), 45.0F);
			helper.assertTrue(chief.hasCalledWarCry(), "the chief should call its war cry at half health, with " + chief.getHealth() + " left");
			helper.assertTrue(chief.hasEffect(MobEffects.STRENGTH) && guard.hasEffect(MobEffects.STRENGTH) && guard.hasEffect(MobEffects.SPEED),
					"the war cry should make the chief and its guards stronger and faster");

			guard.discard();
			chief.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			chief.setDropChance(EquipmentSlot.HEAD, 0.0F);
			chief.setDropChance(EquipmentSlot.CHEST, 0.0F);
			chief.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
			helper.assertFalse(chief.isAlive(), "the chief should die of 1000 damage");

			List<Item> dropped = level.getEntitiesOfClass(ItemEntity.class, area).stream().map(item -> item.getItem().getItem()).distinct().toList();
			helper.assertTrue(dropped.contains(Items.DIAMOND) && dropped.contains(Items.EMERALD), "a chief killed by a player should drop a diamond and emeralds, but dropped " + dropped);
		} finally {
			level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
			GameTestSupport.remove(helper, player);
			helper.killAllEntities();
		}

		helper.succeed();
	}

	@GameTest
	public void chiefHitsHarderThanAGuard(GameTestHelper helper) {
		GameTestSupport.buildFloor(helper);
		GuardEntity guard = spawnGuard(helper, 1, 1, 1);
		GuardEntity chief = spawnGuard(helper, 5, 1, 5);
		chief.makeChief();

		// What a held weapon adds is applied on the entity's next tick.
		helper.runAfterDelay(5, () -> {
			// A war cry in a test running next to this one may have made them stronger.
			guard.removeAllEffects();
			chief.removeAllEffects();
			double guardDamage = guard.getAttributeValue(Attributes.ATTACK_DAMAGE);
			double chiefDamage = chief.getAttributeValue(Attributes.ATTACK_DAMAGE);
			guard.discard();
			chief.discard();
			helper.assertTrue(guardDamage == 6.0, "a guard with its iron sword should deal 6 damage, but deals " + guardDamage);
			helper.assertTrue(chiefDamage == 10.0, "a chief with its diamond sword should deal 10 damage, but deals " + chiefDamage);
			helper.succeed();
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
