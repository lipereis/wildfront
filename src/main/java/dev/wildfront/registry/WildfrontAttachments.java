package dev.wildfront.registry;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import dev.wildfront.Wildfront;
import dev.wildfront.camp.CampState;
import dev.wildfront.discovery.ActiveCache;
import dev.wildfront.dungeon.DungeonState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class WildfrontAttachments {
	/**
	 * Per player: event id to the overworld game time at which that event last triggered.
	 */
	public static final AttachmentType<Map<String, Long>> EVENT_COOLDOWNS = AttachmentRegistry.<Map<String, Long>>create(
			Wildfront.id("event_cooldowns"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

	/**
	 * Per level: forgotten caches that were placed and not yet found, destroyed or expired.
	 */
	public static final AttachmentType<List<ActiveCache>> ACTIVE_CACHES = AttachmentRegistry.createPersistent(
			Wildfront.id("active_caches"), ActiveCache.CODEC.listOf());

	/**
	 * Per level: bandit camps whose guards have been spawned, occupied or cleared.
	 */
	public static final AttachmentType<List<CampState>> CAMPS = AttachmentRegistry.createPersistent(
			Wildfront.id("camps"), CampState.CODEC.listOf());

	/**
	 * Per level: dungeons somebody has interacted with, and how far their puzzles are solved.
	 */
	public static final AttachmentType<List<DungeonState>> DUNGEONS = AttachmentRegistry.createPersistent(
			Wildfront.id("dungeons"), DungeonState.CODEC.listOf());

	private WildfrontAttachments() {
	}

	public static void init() {
		// Loads this class so the attachment types are registered during mod initialization.
	}
}
