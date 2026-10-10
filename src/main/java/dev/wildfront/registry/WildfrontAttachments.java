package dev.wildfront.registry;

import java.util.Map;

import com.mojang.serialization.Codec;

import dev.wildfront.Wildfront;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class WildfrontAttachments {
	/**
	 * Per player: event id to the overworld game time at which that event last triggered.
	 */
	public static final AttachmentType<Map<String, Long>> EVENT_COOLDOWNS = AttachmentRegistry.<Map<String, Long>>create(
			Wildfront.id("event_cooldowns"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

	private WildfrontAttachments() {
	}

	public static void init() {
		// Loads this class so the attachment types are registered during mod initialization.
	}
}
