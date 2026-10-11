package dev.wildfront.command;

import java.util.List;
import java.util.OptionalLong;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import dev.wildfront.Wildfront;
import dev.wildfront.camp.CampManager;
import dev.wildfront.camp.CampState;
import dev.wildfront.config.ConfigManager;
import dev.wildfront.config.EventConfig;
import dev.wildfront.config.HostileCampConfig;
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
						.then(Commands.literal("camp")
								.then(Commands.literal("list").executes(WildfrontCommands::listCamps))
								.then(Commands.literal("reset").executes(WildfrontCommands::resetCamps)))
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

	private static int listCamps(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		List<CampState> camps = CampManager.states(source.getServer().overworld());
		HostileCampConfig config = ConfigManager.get().hostileCamps();

		source.sendSuccess(() -> Component.literal("Wildfront bandit camps (" + (config.enabled() ? "on" : "off") + ", activation radius "
				+ config.activationRadius() + ", " + config.minGuards() + " to " + config.maxGuards() + " guards): " + camps.size() + " visited"), false);

		for (CampState camp : camps) {
			source.sendSuccess(() -> Component.literal(" - " + camp.center().toShortString() + ": "
					+ (camp.cleared() ? "cleared" : camp.guardsLeft() + " guard(s) left")), false);
		}

		return camps.size();
	}

	private static int resetCamps(CommandContext<CommandSourceStack> context) {
		int[] removed = CampManager.reset(context.getSource().getServer().overworld());
		context.getSource().sendSuccess(() -> Component.literal("Removed " + removed[0] + " loaded guard(s) and forgot " + removed[1]
				+ " camp(s). Camps become occupied again when a player comes near."), true);
		return removed[1];
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
				+ (config.eventsEnabled() ? "on" : "off") + ", check every " + config.checkIntervalSeconds() + " s"
				+ ", global cooldown " + config.globalCooldownSeconds() + " s, share radius " + config.shareRadius() + "):"), false);

		if (player != null) {
			long remaining = EventScheduler.remainingCooldown(new PlayerCooldowns(player).lastTrigger(EventScheduler.ANY_EVENT),
					now, EventSettings.ticks(config.globalCooldownSeconds()));
			source.sendSuccess(() -> Component.literal(" Your global cooldown: " + formatRemaining(remaining)), false);
		}

		for (WorldEvent event : WildfrontEvents.all()) {
			EventConfig eventConfig = config.event(event.id());
			EventSettings settings = EventSettings.of(eventConfig);
			StringBuilder line = new StringBuilder(" - ").append(event.id())
					.append(settings.enabled() ? "" : " [disabled]")
					.append(": cooldown ").append(eventConfig.cooldownSeconds()).append(" s")
					.append(", chance ").append(settings.triggerChance());

			if (player != null) {
				OptionalLong last = new PlayerCooldowns(player).lastTrigger(event.id());
				List<String> unmet = Wildfront.EVENTS.unmetConditions(event, player);
				line.append(", your cooldown: ").append(formatRemaining(EventScheduler.remainingCooldown(last, now, settings.cooldownTicks())))
						.append(unmet.isEmpty() ? ", conditions met" : ", waiting for: " + String.join("; ", unmet));
			}

			source.sendSuccess(() -> Component.literal(line.toString()), false);
		}

		return WildfrontEvents.all().size();
	}

	private static String formatRemaining(long ticks) {
		// Rounded up so that "ready" is only shown once the cooldown is really over.
		return ticks == 0 ? "ready" : ((ticks + EventSettings.TICKS_PER_SECOND - 1) / EventSettings.TICKS_PER_SECOND) + " s left";
	}

	private static int trigger(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException {
		String id = StringArgumentType.getString(context, "event");
		WorldEvent event = WildfrontEvents.get(id).orElseThrow(() -> UNKNOWN_EVENT.create(id));

		List<ServerPlayer> participants = Wildfront.EVENTS.forceTrigger(event, target);

		if (participants.isEmpty()) {
			context.getSource().sendFailure(Component.literal("'" + id + "' cannot happen where ").append(target.getDisplayName())
					.append(" is right now (wrong dimension, no suitable place nearby, or too many active already). Nothing was changed."));
			return 0;
		}

		int witnesses = participants.size() - 1;
		context.getSource().sendSuccess(() -> Component.literal("Triggered '" + id + "' for ").append(target.getDisplayName())
				.append(witnesses > 0 ? " and " + witnesses + " nearby player(s)" : ""), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer target) {
		new PlayerCooldowns(target).clear();
		context.getSource().sendSuccess(() -> Component.literal("Cleared Wildfront event cooldowns for ").append(target.getDisplayName()), true);
		return 1;
	}
}
