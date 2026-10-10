package dev.wildfront.command;

import java.util.List;
import java.util.OptionalLong;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import dev.wildfront.Wildfront;
import dev.wildfront.config.ConfigManager;
import dev.wildfront.config.WildfrontConfig;
import dev.wildfront.event.EventScheduler;
import dev.wildfront.event.EventSettings;
import dev.wildfront.event.PlayerCooldowns;
import dev.wildfront.event.WorldEvent;
import dev.wildfront.registry.WildfrontEvents;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator commands for inspecting and testing the event system.
 */
public final class WildfrontCommands {
	private static final DynamicCommandExceptionType UNKNOWN_EVENT = new DynamicCommandExceptionType(
			id -> Component.literal("Unknown Wildfront event '" + id + "'. Known events: " + WildfrontEvents.ids()));

	private WildfrontCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				Commands.literal("wildfront")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("reload").executes(WildfrontCommands::reload))
						.then(Commands.literal("event")
								.then(Commands.literal("list").executes(WildfrontCommands::list))
								.then(Commands.literal("trigger")
										.then(Commands.argument("event", StringArgumentType.word())
												.suggests((context, builder) -> SharedSuggestionProvider.suggest(WildfrontEvents.ids(), builder))
												.executes(context -> trigger(context, context.getSource().getPlayerOrException()))
												.then(Commands.argument("player", EntityArgument.player())
														.executes(context -> trigger(context, EntityArgument.getPlayer(context, "player"))))))
								.then(Commands.literal("reset")
										.executes(context -> reset(context, context.getSource().getPlayerOrException()))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(context -> reset(context, EntityArgument.getPlayer(context, "player"))))))));
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		List<String> problems = ConfigManager.reload();

		if (problems.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("Wildfront config reloaded."), true);
			return 1;
		}

		context.getSource().sendFailure(Component.literal("Wildfront config reloaded with " + problems.size() + " problem(s):"));

		for (String problem : problems) {
			context.getSource().sendFailure(Component.literal(" - " + problem));
		}

		return 0;
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		WildfrontConfig config = ConfigManager.get();
		ServerPlayer player = source.getPlayer();
		long now = source.getServer().overworld().getGameTime();

		source.sendSuccess(() -> Component.literal("Wildfront events (automatic events "
				+ (config.eventsEnabled() ? "on" : "off") + ", check every " + config.checkIntervalSeconds() + " s):"), false);

		for (WorldEvent event : WildfrontEvents.all()) {
			EventSettings settings = event.settings(config);
			StringBuilder line = new StringBuilder(" - ").append(event.id())
					.append(settings.enabled() ? "" : " [disabled]")
					.append(": cooldown ").append(settings.cooldownTicks() / EventSettings.TICKS_PER_SECOND).append(" s")
					.append(", chance ").append(settings.triggerChance());

			if (player != null) {
				OptionalLong last = new PlayerCooldowns(player).lastTrigger(event.id());
				long remaining = EventScheduler.remainingCooldown(last, now, settings.cooldownTicks());
				line.append(", your cooldown: ").append(remaining == 0 ? "ready" : (remaining / EventSettings.TICKS_PER_SECOND) + " s left");
			}

			source.sendSuccess(() -> Component.literal(line.toString()), false);
		}

		return WildfrontEvents.all().size();
	}

	private static int trigger(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
		String id = StringArgumentType.getString(context, "event");
		WorldEvent event = WildfrontEvents.get(id).orElseThrow(() -> UNKNOWN_EVENT.create(id));

		Wildfront.EVENTS.forceTrigger(event, target);
		context.getSource().sendSuccess(() -> Component.literal("Triggered '" + id + "' for ").append(target.getDisplayName()), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer target) {
		new PlayerCooldowns(target).clear();
		context.getSource().sendSuccess(() -> Component.literal("Cleared Wildfront event cooldowns for ").append(target.getDisplayName()), true);
		return 1;
	}
}
