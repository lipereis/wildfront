package dev.wildfront.event.trigger;

import java.util.ArrayList;
import java.util.List;

import dev.wildfront.config.EventConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;

/**
 * The conditions every event can be configured with.
 */
public final class TriggerConditions {
	private TriggerConditions() {
	}

	/**
	 * Builds the conditions requested by an event's configuration. Settings that allow everything
	 * produce no condition, so an unrestricted event costs nothing to check.
	 */
	public static List<TriggerCondition> fromConfig(EventConfig config) {
		List<TriggerCondition> conditions = new ArrayList<>(3);

		if (config.minDistanceFromSpawn() > 0) {
			conditions.add(minDistanceFromSpawn(config.minDistanceFromSpawn()));
		}

		if (config.timeOfDay() != TimeOfDay.ANY) {
			conditions.add(timeOfDay(config.timeOfDay()));
		}

		if (config.weather() != Weather.ANY) {
			conditions.add(weather(config.weather()));
		}

		return conditions;
	}

	public static TriggerCondition minDistanceFromSpawn(int blocks) {
		return TriggerCondition.of("at least " + blocks + " blocks from world spawn", context -> {
			LevelData.RespawnData spawn = context.level().getRespawnData();

			// Outside the spawn dimension there is no meaningful distance to measure.
			if (spawn.dimension() != context.level().dimension()) {
				return true;
			}

			BlockPos position = context.player().blockPosition();
			long dx = position.getX() - spawn.pos().getX();
			long dz = position.getZ() - spawn.pos().getZ();
			return dx * dx + dz * dz >= (long) blocks * blocks;
		});
	}

	public static TriggerCondition timeOfDay(TimeOfDay timeOfDay) {
		return TriggerCondition.of("time of day " + timeOfDay, context -> {
			ServerLevel level = context.level();
			return level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime()
					&& timeOfDay.matches(level.getOverworldClockTime());
		});
	}

	/**
	 * Uses the level-wide weather; biomes where it never rains are not taken into account.
	 */
	public static TriggerCondition weather(Weather weather) {
		return TriggerCondition.of("weather " + weather, context -> {
			ServerLevel level = context.level();
			return level.dimensionType().hasSkyLight() && weather.matches(level.isRaining(), level.isThundering());
		});
	}
}
