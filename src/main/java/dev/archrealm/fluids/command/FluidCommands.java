package dev.archrealm.fluids.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.config.FluidConfig;
import dev.archrealm.fluids.config.FluidConfigManager;
import dev.archrealm.fluids.world.FluidEngine;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /arfluids} - administration of the simulation.
 *
 * <ul>
 *   <li>{@code /arfluids stats} - live counters for the current dimension</li>
 *   <li>{@code /arfluids clear [radius]} - remove realistic fluid around you</li>
 *   <li>{@code /arfluids wake [radius]} - force the simulation to re-examine loaded chunks</li>
 *   <li>{@code /arfluids reload} - re-read the configuration file</li>
 *   <li>{@code /arfluids version} - mod information</li>
 * </ul>
 */
public final class FluidCommands {
	private FluidCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("arfluids")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("stats").executes(FluidCommands::stats))
				.then(Commands.literal("version").executes(FluidCommands::version))
				.then(Commands.literal("reload").executes(FluidCommands::reload))
				.then(Commands.literal("clear")
						.executes(context -> clear(context, 16))
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
								.executes(context -> clear(context, IntegerArgumentType.getInteger(context, "radius")))))
				.then(Commands.literal("wake")
						.executes(context -> wake(context, 32))
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
								.executes(context -> wake(context, IntegerArgumentType.getInteger(context, "radius")))))
				.executes(FluidCommands::stats));
	}

	private static int stats(CommandContext<CommandSourceStack> context) {
		ServerLevel level = context.getSource().getLevel();
		FluidEngine.StatsSnapshot snapshot = FluidEngine.snapshot(level);
		FluidConfig config = ArchRealmFluids.config();
		context.getSource().sendSuccess(() -> Component.translatable("command.archrealm_fluids.stats",
				snapshot.activeCells(), snapshot.parkedCells(), snapshot.queuedChunks(), snapshot.visited(),
				snapshot.moved(), config.cellUpdatesPerStep, config.stepsPerSecond, snapshot.steps()), false);
		return 1;
	}

	private static int version(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.translatable("command.archrealm_fluids.version",
				ArchRealmFluids.version()), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		ArchRealmFluids.reloadConfig();
		context.getSource().sendSuccess(() -> Component.translatable("command.archrealm_fluids.reload"), false);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> context, int radius) {
		ServerLevel level = context.getSource().getLevel();
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		int removed = FluidEngine.clearArea(level, player.blockPosition(), radius);
		context.getSource().sendSuccess(() -> Component.translatable("command.archrealm_fluids.clear", removed), false);
		return removed;
	}

	private static int wake(CommandContext<CommandSourceStack> context, int radius) {
		ServerLevel level = context.getSource().getLevel();
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		FluidEngine.wakeArea(level, player.blockPosition(), radius);
		context.getSource().sendSuccess(() -> Component.translatable("command.archrealm_fluids.wake", radius), false);
		return 1;
	}
}
