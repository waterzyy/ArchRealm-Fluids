package dev.archrealm.fluids.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.config.FluidConfig;
import dev.archrealm.fluids.config.FluidConfigManager;
import dev.archrealm.fluids.registry.ModBlockEntities;
import dev.archrealm.fluids.registry.ModFluids;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Client entry point: the fluid look, the key bindings and the performance overlay.
 *
 * <p>Water and lava are drawn with vanilla's own {@link FluidModel} and tessellator, so Sodium, Iris
 * and shader packs see exactly the geometry they expect. The mod only decides <em>whether</em> that
 * geometry is emitted, which is what the water view toggle does.
 */
public final class ArchRealmFluidsClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, "controls"));

	private static KeyMapping settingsKey;
	private static KeyMapping viewKey;
	private static KeyMapping overlayKey;

	@Override
	public void onInitializeClient() {
		FluidView.loadFromConfig();
		registerFluidRendering();
		registerBlockEntityRenderers();
		registerKeys();
		registerOverlay();
		FluidClientCommands.register();
		ArchRealmFluids.LOGGER.info("ArchRealm Fluids client ready (view: {})", FluidView.mode());
	}

	// --- rendering --------------------------------------------------------

	private static void registerFluidRendering() {
		Material waterStill = new Material(Identifier.withDefaultNamespace("block/water_still"));
		Material waterFlow = new Material(Identifier.withDefaultNamespace("block/water_flow"));
		Material waterOverlay = new Material(Identifier.withDefaultNamespace("block/water_overlay"));
		FluidRenderingRegistry.register(ModFluids.WATER,
				new FluidModel.Unbaked(waterStill, waterFlow, waterOverlay, BlockTintSources.water()),
				new ToggleableHandler());

		Material lavaStill = new Material(Identifier.withDefaultNamespace("block/lava_still"));
		Material lavaFlow = new Material(Identifier.withDefaultNamespace("block/lava_flow"));
		FluidRenderingRegistry.register(ModFluids.LAVA,
				new FluidModel.Unbaked(lavaStill, lavaFlow, null, null),
				new ToggleableHandler());
	}

	/**
	 * Emits the normal fluid surface in mesh view and nothing at all in particle view, where
	 * {@link FluidParticles} draws drifting drops instead.
	 */
	private static final class ToggleableHandler implements FluidRenderHandler {
		@Override
		public void renderFluid(FluidRenderer fluidRenderer, BlockPos pos, BlockAndTintGetter level,
				FluidRenderer.Output output, BlockState blockState, FluidState fluidState) {
			if (FluidView.mode() == FluidView.Mode.MESH) {
				FluidRenderHandler.super.renderFluid(fluidRenderer, pos, level, output, blockState, fluidState);
			}
		}
	}

	private static void registerBlockEntityRenderers() {
		BlockEntityRendererRegistry.register(ModBlockEntities.WOBBLE, context -> new WobbleBlockRenderer());
	}

	// --- key bindings -----------------------------------------------------

	private static void registerKeys() {
		// Since 26.3 the KeyMapping constructor registers itself with the vanilla options,
		// so no extra key binding API is needed.
		settingsKey = new KeyMapping("key.archrealm_fluids.settings",
				InputConstants.Type.KEYBOARD, InputConstants.KEY_M, CATEGORY);
		viewKey = new KeyMapping("key.archrealm_fluids.view",
				InputConstants.Type.KEYBOARD, InputConstants.KEY_N, CATEGORY);
		overlayKey = new KeyMapping("key.archrealm_fluids.overlay",
				InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			FluidParticles.tick(client);
			while (settingsKey.consumeClick()) {
				client.gui.setScreen(new FluidSettingsScreen(client.gui.screen()));
			}
			boolean changed = false;
			while (viewKey.consumeClick()) {
				FluidView.toggleMode();
				changed = true;
			}
			while (overlayKey.consumeClick()) {
				FluidView.toggleOverlay();
				changed = true;
			}
			if (changed) {
				persistDisplaySettings();
				announce(client);
			}
		});
	}

	private static void announce(Minecraft client) {
		if (client.player == null) {
			return;
		}
		client.player.sendSystemMessage(Component.translatable("message.archrealm_fluids.view",
				Component.translatable(FluidView.mode() == FluidView.Mode.MESH
						? "view.archrealm_fluids.mesh" : "view.archrealm_fluids.particles"),
				Component.translatable(FluidView.overlay()
						? "view.archrealm_fluids.overlay_on" : "view.archrealm_fluids.overlay_off")));
	}

	static void persistDisplaySettings() {
		FluidConfig config = ArchRealmFluids.config();
		config.startInParticleView = FluidView.mode() == FluidView.Mode.PARTICLES;
		config.debugOverlay = FluidView.overlay();
		FluidConfigManager.save();
	}

	// --- overlay ----------------------------------------------------------

	private static void registerOverlay() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, "stats"),
				(graphics, deltaTracker) -> {
					if (!FluidView.overlay()) {
						return;
					}
					Minecraft client = Minecraft.getInstance();
					if (client.player == null || client.level == null) {
						return;
					}
					int cells = FluidParticles.count();
					String line = Component.translatable("overlay.archrealm_fluids.stats",
							Component.translatable(FluidView.mode() == FluidView.Mode.MESH
									? "view.archrealm_fluids.mesh" : "view.archrealm_fluids.particles"),
							cells, client.getFps()).getString();
					graphics.fill(2, 2, 2 + client.font.width(line) + 4, 14, 0x66000000);
					graphics.text(client.font, line, 4, 5, 0xFFE0F7FF, true);
				});
	}
}
