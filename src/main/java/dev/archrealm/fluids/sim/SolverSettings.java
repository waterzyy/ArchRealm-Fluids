package dev.archrealm.fluids.sim;

/**
 * Tunable constants of the simulation. Immutable: the server builds one instance from its JSON
 * configuration file and reuses it for every step, so there is no per-tick allocation.
 */
public record SolverSettings(
		int maxUnits,
		int maxCellUpdatesPerStep,
		boolean pressureEnabled,
		int pressureMaxCells,
		int pressureWorkBudget,
		int horizontalFlowDelayMultiplier
) {
	public static final SolverSettings DEFAULT = new SolverSettings(
			8,      // maxUnits - one full block of fluid
			2048,   // cells handled per solver step
			true,   // hydrostatic pressure / communicating vessels
			1024,   // largest body that participates in pressure equalisation
			8192,   // total pressure flood-fill work per step
			1       // global viscosity multiplier (1 = vanilla-like speed)
	);

	public SolverSettings {
		if (maxUnits < 1 || maxUnits > 16) {
			throw new IllegalArgumentException("maxUnits must be in [1, 16], got " + maxUnits);
		}
		if (maxCellUpdatesPerStep < 1) {
			throw new IllegalArgumentException("maxCellUpdatesPerStep must be positive");
		}
		if (pressureMaxCells < 1 || pressureWorkBudget < 1) {
			throw new IllegalArgumentException("pressure limits must be positive");
		}
		if (horizontalFlowDelayMultiplier < 1) {
			throw new IllegalArgumentException("horizontalFlowDelayMultiplier must be >= 1");
		}
	}

	public SolverSettings withMaxCellUpdatesPerStep(int value) {
		return new SolverSettings(maxUnits, value, pressureEnabled, pressureMaxCells,
				pressureWorkBudget, horizontalFlowDelayMultiplier);
	}

	/** Rate at which a fluid may move sideways, including the global viscosity multiplier. */
	public int horizontalRate(FluidKind kind) {
		return Math.max(1, kind.horizontalRate() / horizontalFlowDelayMultiplier);
	}

	/** Rate at which a fluid may move vertically, including the global viscosity multiplier. */
	public int verticalRate(FluidKind kind) {
		return Math.max(1, kind.verticalRate() / horizontalFlowDelayMultiplier);
	}

	/** Whether a fluid is allowed to move on the given simulation step (viscosity). */
	public boolean ticksOnStep(FluidKind kind, long step) {
		return step % (kind.flowDelay() * (long) horizontalFlowDelayMultiplier) == 0L;
	}
}
