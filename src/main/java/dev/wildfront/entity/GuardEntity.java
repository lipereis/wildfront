package dev.wildfront.entity;

import java.util.UUID;

import dev.wildfront.camp.CampManager;
import dev.wildfront.registry.WildfrontLootTables;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A human guard of a hostile camp: attacks players on sight, alerts the rest of its camp when it
 * spots one, and returns to its post when it has one (see {@code Mob.setHomeTo}).
 *
 * <p>How a guard fights follows from what it holds, as with the game's skeletons: with a bow it
 * keeps its distance and shoots, with anything else it closes in.
 *
 * <p>Some camps are led by a chief: a bigger, tougher, harder-hitting guard with its own skin and
 * far more health, shown with a boss bar, who rallies the camp with a war cry when badly hurt.
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
	public static final double CHIEF_HEALTH = 80.0;
	public static final double CHIEF_SCALE = 1.2;

	/** Bare-handed damage of a chief; its diamond sword adds 6, an ordinary guard's 1 plus 5 for iron. */
	public static final double CHIEF_ATTACK_DAMAGE = 4.0;

	/** A chief's armour, as much as a player in iron helmet and chestplate, without hiding its skin. */
	public static final double CHIEF_ARMOR = 8.0;

	/** Whether a guard is a chief; sent to clients, which draw chiefs with their own skin. */
	private static final EntityDataAccessor<Boolean> DATA_CHIEF = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.BOOLEAN);

	/** How far away a guard notices a player. */
	public static final double SIGHT = 24.0;

	/** War cry: how far it carries and how long its effects last, in ticks. */
	public static final double WAR_CRY_RADIUS = 24.0;
	public static final int WAR_CRY_TICKS = 300;

	/** Ticks between two shots, and the distance an archer tries to keep. As for skeletons. */
	private static final int SHOT_INTERVAL = 40;
	private static final float SHOT_RANGE = 15.0F;

	private static final String CHIEF_KEY = "wildfront_chief";
	private static final String WAR_CRY_KEY = "wildfront_war_cry";

	private final RangedBowAttackGoal<GuardEntity> bowGoal = new RangedBowAttackGoal<>(this, 1.0, SHOT_INTERVAL, SHOT_RANGE);
	private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.0, false);
	private final ServerBossEvent bossEvent = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.wildfront.guard_chief"),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);

	private boolean warCryDone;

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
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CHIEF, false);
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

	/**
	 * Turns the guard into a camp chief: bigger, tougher, harder-hitting, with a diamond sword, far
	 * more health and a skin of its own. Call before the guard is added to the world.
	 */
	public void makeChief() {
		this.entityData.set(DATA_CHIEF, true);
		this.xpReward = 30;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(CHIEF_HEALTH);
		setHealth((float) CHIEF_HEALTH);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(CHIEF_ATTACK_DAMAGE);
		getAttribute(Attributes.ARMOR).setBaseValue(CHIEF_ARMOR);
		getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
		getAttribute(Attributes.SCALE).setBaseValue(CHIEF_SCALE);
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		setDropChance(EquipmentSlot.MAINHAND, 0.25F);
	}

	public boolean isChief() {
		return this.entityData.get(DATA_CHIEF);
	}

	public boolean hasCalledWarCry() {
		return this.warCryDone;
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
	public void setTarget(LivingEntity target) {
		LivingEntity before = getTarget();
		super.setTarget(target);

		// The moment a guard spots a player, the rest of its camp knows too.
		if (before == null && target instanceof Player player && level() instanceof ServerLevel serverLevel) {
			CampManager.raiseAlarm(serverLevel, this, player);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && isChief() && !this.warCryDone && isAlive() && getHealth() <= getMaxHealth() / 2.0F) {
			warCry(level);
		}

		return hurt;
	}

	/**
	 * Once, when the chief is down to half its health: it and the guards of its camp nearby fight
	 * harder and faster for a while.
	 */
	private void warCry(ServerLevel level) {
		this.warCryDone = true;
		playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 1.2F);

		for (GuardEntity guard : level.getEntitiesOfClass(GuardEntity.class, getBoundingBox().inflate(WAR_CRY_RADIUS),
				guard -> guard.isAlive() && CampManager.sameCamp(this, guard))) {
			guard.addEffect(new MobEffectInstance(MobEffects.STRENGTH, WAR_CRY_TICKS, 0));
			guard.addEffect(new MobEffectInstance(MobEffects.SPEED, WAR_CRY_TICKS, 0));
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (isChief()) {
			this.bossEvent.setProgress(getHealth() / getMaxHealth());
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);

		if (isChief()) {
			this.bossEvent.addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
	}

	/**
	 * @return whether the player sees this chief's boss bar
	 */
	public boolean showsBossBarTo(ServerPlayer player) {
		return this.bossEvent.getPlayers().contains(player);
	}

	@Override
	protected Component getTypeName() {
		return isChief() ? Component.translatable("entity.wildfront.guard_chief") : super.getTypeName();
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);

		if (isChief()) {
			dropFromLootTable(level, source, killedByPlayer, WildfrontLootTables.GUARD_CHIEF);
		}
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
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(CHIEF_KEY, isChief());
		output.putBoolean(WAR_CRY_KEY, this.warCryDone);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		// Health, size, armour, damage and weapon of a chief are saved by the game itself.
		this.entityData.set(DATA_CHIEF, input.getBooleanOr(CHIEF_KEY, false));
		this.warCryDone = input.getBooleanOr(WAR_CRY_KEY, false);
		this.xpReward = isChief() ? 30 : 8;
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
