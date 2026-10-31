package dev.archrealm.fluids.client;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.config.FluidConfig;
import dev.archrealm.fluids.config.FluidConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The in-game settings screen (bound to {@code M} by default).
 *
 * <p>Everything here writes straight into {@link FluidConfig} and is saved immediately, so there is
 * no "apply" step and no way to end up with unsaved values. Distances are limited to sane ranges and
 * each change is validated again by the config layer before it reaches the simulation.
 */
public final class FluidSettingsScreen extends Screen {
	private static final int ROW_WIDTH = 210;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 24;

	private final @Nullable Screen parent;

	public FluidSettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("screen.archrealm_fluids.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		FluidConfig config = ArchRealmFluids.config();
		int left = this.width / 2 - ROW_WIDTH / 2;
		int y = this.height / 2 - 72;

		addRenderableWidget(new IntSlider(left, y, ROW_WIDTH, ROW_HEIGHT,
				"option.archrealm_fluids.water_distance", 16, 160, config.waterDistance,
				value -> config.waterDistance = value));
		y += ROW_GAP;
		addRenderableWidget(new IntSlider(left, y, ROW_WIDTH, ROW_HEIGHT,
				"option.archrealm_fluids.lava_distance", 8, 128, config.lavaDistance,
				value -> config.lavaDistance = value));
		y += ROW_GAP;
		addRenderableWidget(new IntSlider(left, y, ROW_WIDTH, ROW_HEIGHT,
				"option.archrealm_fluids.budget", 256, 16384, config.cellUpdatesPerStep,
				value -> config.cellUpdatesPerStep = value));
		y += ROW_GAP;
		addRenderableWidget(Button.builder(viewLabel(), button -> {
			FluidView.toggleMode();
			ArchRealmFluidsClient.persistDisplaySettings();
			button.setMessage(viewLabel());
		}).bounds(left, y, ROW_WIDTH, ROW_HEIGHT).build());
		y += ROW_GAP;
		addRenderableWidget(Button.builder(overlayLabel(), button -> {
			FluidView.toggleOverlay();
			ArchRealmFluidsClient.persistDisplaySettings();
			button.setMessage(overlayLabel());
		}).bounds(left, y, ROW_WIDTH, ROW_HEIGHT).build());
		y += ROW_GAP;
		addRenderableWidget(Button.builder(steamLabel(), button -> {
			config.reactionParticles = !config.reactionParticles;
			FluidConfigManager.save();
			button.setMessage(steamLabel());
		}).bounds(left, y, ROW_WIDTH, ROW_HEIGHT).build());
		y += ROW_GAP;
		int halfWidth = (ROW_WIDTH - 4) / 2;
		addRenderableWidget(Button.builder(Component.translatable("button.archrealm_fluids.clear"),
				button -> clearNearby()).bounds(left, y, halfWidth, ROW_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(left + halfWidth + 4, y, halfWidth, ROW_HEIGHT).build());
	}

	private static Component viewLabel() {
		return Component.translatable("option.archrealm_fluids.view",
				Component.translatable(FluidView.mode() == FluidView.Mode.MESH
						? "view.archrealm_fluids.mesh" : "view.archrealm_fluids.particles"));
	}

	private static Component overlayLabel() {
		return Component.translatable("option.archrealm_fluids.overlay",
				Component.translatable(FluidView.overlay()
						? "option.archrealm_fluids.on" : "option.archrealm_fluids.off"));
	}

	private static Component steamLabel() {
		return Component.translatable("option.archrealm_fluids.steam",
				Component.translatable(ArchRealmFluids.config().reactionParticles
						? "option.archrealm_fluids.on" : "option.archrealm_fluids.off"));
	}

	private void clearNearby() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.connection != null) {
			client.player.connection.sendCommand("arfluids clear 48");
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2,
				this.height / 2 - 96, 0xFFFFFFFF, true);
	}

	@Override
	public void onClose() {
		FluidConfigManager.save();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Slider for an integer setting; it stores the value as it is dragged. */
	private static final class IntSlider extends AbstractSliderButton {
		private final String translationKey;
		private final int min;
		private final int max;
		private final java.util.function.IntConsumer setter;

		private IntSlider(int x, int y, int width, int height, String translationKey, int min, int max, int value,
				java.util.function.IntConsumer setter) {
			super(x, y, width, height, Component.empty(), (value - min) / (double) Math.max(1, max - min));
			this.translationKey = translationKey;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.value = Mth.clamp((value - min) / (double) Math.max(1, max - min), 0.0, 1.0);
			updateMessage();
		}

		private int current() {
			return min + (int) Math.round(this.value * (max - min));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(translationKey, current()));
		}

		@Override
		protected void applyValue() {
			setter.accept(current());
			ArchRealmFluids.config().validate();
			FluidConfigManager.save();
		}
	}
}
