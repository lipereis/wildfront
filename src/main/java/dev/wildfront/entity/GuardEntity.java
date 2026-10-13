package dev.wildfront.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;

/**
 * A human guard of a hostile camp: attacks players on sight, calls the other guards when hurt,
 * and returns to its post when it has one (see {@code Mob.setHomeTo}).
 *
 * <p>How a guard fights follows from what it holds, as with the game's skeletons: with a bow it
 * keeps its distance and shoots, with anything else it closes in. Its role is therefore nothing
 * but its weapon, which the game saves and sends to clients already.
 *
 * <p>All behaviour is built from the game's own goals and runs on the server. The client only
 * draws the guard.
 */
public class GuardEntity extends Monster implements RangedAttackMob {
	/** What a guard is armed with when it takes up its post. */
	public enum Role {
		/** Iron sword. */
		MELEE,
		/** Bow. */
		RANGED
	}

	public static final double MAX_HEALTH = 24.0;

	/** How far away a guard notices a player. */
	public static final double SIGHT = 24.0;

	/** Ticks between two shots, and the distance an archer tries to keep. As for skeletons. */
	private static final int SHOT_INTERVAL = 40;
	private static final float SHOT_RANGE = 15.0F;

	private final RangedBowAttackGoal<GuardEntity> bowGoal = new RangedBowAttackGoal<>(this, 1.0, SHOT_INTERVAL, SHOT_RANGE);
	private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.0, false);

	public GuardEntity(EntityType<? extends GuardEntity> type, Level level) {
		super(type, level);
		this.xpReward = 8;
		reassessWeaponGoal();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				// Bare hands; the sword it is given adds to this.
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, SIGHT)
				.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void registerGoals() {
		// The attack goal is not among these: it depends on the weapon, see reassessWeaponGoal.
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.9));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(GuardEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/**
	 * Arms the guard for a role, replacing what it held.
	 */
	public void equip(Role role) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(role == Role.RANGED ? Items.BOW : Items.IRON_SWORD));
	}

	public boolean isArcher() {
		return getMainHandItem().is(Items.BOW);
	}

	/**
	 * Makes the guard fight the way its weapon calls for.
	 */
	public void reassessWeaponGoal() {
		if (level() == null || level().isClientSide()) {
			return;
		}

		this.goalSelector.removeGoal(this.meleeGoal);
		this.goalSelector.removeGoal(this.bowGoal);
		this.goalSelector.addGoal(2, isArcher() ? this.bowGoal : this.meleeGoal);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		populateDefaultEquipmentSlots(level.getRandom(), difficulty);
		return data;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		equip(Role.MELEE);
	}

	@Override
	public void onEquipItem(EquipmentSlot slot, ItemStack oldStack, ItemStack newStack) {
		super.onEquipItem(slot, oldStack, newStack);

		if (!level().isClientSide()) {
			reassessWeaponGoal();
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		reassessWeaponGoal();
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		ItemStack bow = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
		ItemStack ammunition = getProjectile(bow);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammunition, power, bow);
		double dx = target.getX() - getX();
		double dy = target.getY(1.0 / 3.0) - arrow.getY();
		double dz = target.getZ() - getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);

		// Aimed a little high to allow for the arrow's drop, as skeletons do.
		Projectile.spawnProjectileUsingShoot(arrow, serverLevel, ammunition, dx, dy + distance * 0.2, dz, 1.6F, rangedAttackUncertainty(serverLevel));
		playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
	}
}
