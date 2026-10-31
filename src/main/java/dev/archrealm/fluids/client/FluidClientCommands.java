package dev.archrealm.fluids.client;

import dev.archrealm.fluids.ArchRealmFluids;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The {@code /fluid} client commands, mirroring the ones players know from the original mod.
 *
 * <p>They run on the client and never touch the server state directly: {@code check} reports what the
 * client sees, {@code view} switches the water look and {@code clear} forwards the removal to the
 * server command, so it needs the same permission as {@code /arfluids clear}.
 */
public final class FluidClientCommands {
	private FluidClientCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("fluid")
						.then(ClientCommands.literal("stats").executes(context -> stats(context.getSource())))
						.then(ClientCommands.literal("clear").executes(context -> clear(context.getSource())))
						.then(ClientCommands.literal("view")
								.then(ClientCommands.literal("mesh").executes(context -> view(context.getSource(),
										FluidView.Mode.MESH)))
								.then(ClientCommands.literal("fluid").executes(context -> view(context.getSource(),
										FluidView.Mode.PARTICLES)))
								.then(ClientCommands.literal("particles").executes(context -> view(context.getSource(),
										FluidView.Mode.PARTICLES))))));
	}

	private static int stats(FabricClientCommandSource source) {
		Minecraft client = Minecraft.getInstance();
		int cells = FluidParticles.count();
		source.sendFeedback(Component.translatable("command.archrealm_fluids.client_stats", cells,
				Component.translatable(FluidView.mode() == FluidView.Mode.MESH
						? "view.archrealm_fluids.mesh" : "view.archrealm_fluids.particles"),
				client.getFps()));
		return 1;
	}

	private static int clear(FabricClientCommandSource source) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			source.sendError(Component.translatable("command.archrealm_fluids.client_no_world"));
			return 0;
		}
		client.player.connection.sendCommand("arfluids clear 48");
		source.sendFeedback(Component.translatable("command.archrealm_fluids.client_clear"));
		return 1;
	}

	private static int view(FabricClientCommandSource source, FluidView.Mode mode) {
		FluidView.setMode(mode);
		ArchRealmFluidsClient.persistDisplaySettings();
		source.sendFeedback(Component.translatable("command.archrealm_fluids.client_view",
				Component.translatable(mode == FluidView.Mode.MESH
						? "view.archrealm_fluids.mesh" : "view.archrealm_fluids.particles")));
		return 1;
	}
}
