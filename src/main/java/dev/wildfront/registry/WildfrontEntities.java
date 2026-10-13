package dev.wildfront.registry;

import dev.wildfront.Wildfront;
import dev.wildfront.entity.GuardEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity types added by the mod. An entity type has to exist on the client too, which is why the
 * mod must be installed by everyone who joins a server that runs it.
 */
public final class WildfrontEntities {
	public static final ResourceKey<EntityType<?>> GUARD_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Wildfront.id("guard"));

	/**
	 * {@code wildfront:guard}, the size of a player. Never spawns on its own: no biome lists it.
	 */
	public static final EntityType<GuardEntity> GUARD = Registry.register(BuiltInRegistries.ENTITY_TYPE, GUARD_KEY,
			EntityType.Builder.<GuardEntity>of(GuardEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.clientTrackingRange(8)
					.notInPeaceful()
					.build(GUARD_KEY));

	private WildfrontEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(GUARD, GuardEntity.createAttributes());
	}
}
