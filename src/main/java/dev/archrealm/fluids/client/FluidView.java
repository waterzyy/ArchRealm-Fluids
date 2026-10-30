package dev.archrealm.fluids.client;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.config.FluidConfig;

/**
 * Client only display state: how the fluid is drawn and whether the performance overlay is visible.
 *
 * <p>These are cosmetic choices, so they live on the client and are never synchronised with the
 * server, which only ever cares about the physical simulation.
 */
public final class FluidView {
	public enum Mode {
		/** Smooth vanilla-style surface (default). */
		MESH,
		/** Individual drifting droplets instead of a surface. */
		PARTICLES
	}

	private static Mode mode = Mode.MESH;
	private static boolean overlay;

	private FluidView() {
	}

	public static Mode mode() {
		return mode;
	}

	public static void setMode(Mode newMode) {
		mode = newMode == null ? Mode.MESH : newMode;
	}

	public static Mode toggleMode() {
		mode = mode == Mode.MESH ? Mode.PARTICLES : Mode.MESH;
		return mode;
	}

	public static boolean overlay() {
		return overlay;
	}

	public static void setOverlay(boolean enabled) {
		overlay = enabled;
	}

	public static boolean toggleOverlay() {
		overlay = !overlay;
		return overlay;
	}

	/** Applies the persisted defaults from the shared configuration. */
	public static void loadFromConfig() {
		FluidConfig config = ArchRealmFluids.config();
		mode = config.startInParticleView ? Mode.PARTICLES : Mode.MESH;
		overlay = config.debugOverlay;
	}
}
