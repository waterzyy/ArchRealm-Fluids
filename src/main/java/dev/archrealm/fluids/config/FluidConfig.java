package dev.archrealm.fluids.config;

import java.util.ArrayList;
import java.util.List;

import dev.archrealm.fluids.sim.SolverSettings;

/**
 * User facing configuration, serialised to {@code config/archrealm_fluids.json}.
 *
 * <p>Every field is plain data so the file can be edited by hand or by a modpack. Unknown keys are
 * ignored and missing keys fall back to these defaults, which keeps old configs working across
 * updates.
 */
public final class FluidConfig {
	// --- simulation -------------------------------------------------------
	/** Master switch for the simulation. Fluid stays in the world but stops moving when false. */
	public boolean enabled = true;
	/** Maximum number of cells the solver may touch per simulation step (10 steps per second). */
	public int cellUpdatesPerStep = 2048;
	/** Whether hydrostatic pressure is simulated (communicating vessels, U-tubes, rising water). */
	public boolean pressure = true;
	/** Largest connected body that takes part in pressure equalisation. */
	public int pressureMaxCells = 1024;
	/** Total pressure flood-fill work allowed per step. */
	public int pressureWorkBudget = 8192;
	/** Viscosity multiplier: 1 = designed speed, 2 = everything moves half as fast. */
	public int viscosity = 1;
	/** Simulation steps per second (1-20). Lower values are cheaper but visually choppier. */
	public int stepsPerSecond = 10;

	// --- world rules ------------------------------------------------------
	/** Water turns lava into stone (and obsidian for full lava cells) with steam. */
	public boolean waterLavaReaction = true;
	/** Lava sets flammable blocks on fire, honouring the {@code doFireTick} gamerule. */
	public boolean lavaIgnitesFire = true;
	/** Water extinguishes burning blocks next to it. */
	public boolean waterExtinguishesFire = true;
	/** Water hydrates farmland it flows over (like vanilla water). */
	public boolean waterHydratesFarmland = true;

	// --- performance ------------------------------------------------------
	/** Hard cap on the simulated distance, in blocks (0 = no cap). */
	public int simulationRadius = 128;
	/** Water further than this many blocks from every player is parked (0 = never park water). */
	public int waterDistance = 96;
	/** Lava further than this many blocks from every player is parked (0 = never park lava). */
	public int lavaDistance = 48;
	/** Keep parked fluid flowing block by block, far away from every player. */
	public boolean parkedFlow = true;
	/** Parked cells are advanced once every this many ticks. */
	public int parkedIntervalTicks = 4;
	/** How many parked cells may move per parked pass. */
	public int parkedCellsPerTick = 64;
	/** Re-activate a chunk's fluid when the chunk is loaded so it can settle again. */
	public boolean wakeOnChunkLoad = true;
	/** Maximum chunks that may be woken per tick. */
	public int wakeChunksPerTick = 2;

	// --- feedback ---------------------------------------------------------
	/** Water and lava produce splash/steam particles when they interact. */
	public boolean reactionParticles = true;
	/** Show the performance overlay in the top left corner. */
	public boolean debugOverlay = false;
	/** Start with the particle water view instead of the smooth surface. */
	public boolean startInParticleView = false;

	/** Clamps values into sane ranges. Called after loading and before building settings. */
	public void validate() {
		List<String> fixes = new ArrayList<>();
		if (cellUpdatesPerStep < 16) {
			cellUpdatesPerStep = 16;
			fixes.add("cellUpdatesPerStep raised to 16");
		}
		if (cellUpdatesPerStep > 200_000) {
			cellUpdatesPerStep = 200_000;
			fixes.add("cellUpdatesPerStep limited to 200000");
		}
		if (pressureMaxCells < 16) {
			pressureMaxCells = 16;
			fixes.add("pressureMaxCells raised to 16");
		}
		if (pressureWorkBudget < 16) {
			pressureWorkBudget = 16;
			fixes.add("pressureWorkBudget raised to 16");
		}
		if (viscosity < 1) {
			viscosity = 1;
			fixes.add("viscosity raised to 1");
		}
		if (viscosity > 16) {
			viscosity = 16;
			fixes.add("viscosity limited to 16");
		}
		if (stepsPerSecond < 1) {
			stepsPerSecond = 1;
			fixes.add("stepsPerSecond raised to 1");
		}
		if (stepsPerSecond > 20) {
			stepsPerSecond = 20;
			fixes.add("stepsPerSecond limited to 20");
		}
		if (simulationRadius < 0) {
			simulationRadius = 0;
			fixes.add("simulationRadius raised to 0");
		}
		if (wakeChunksPerTick < 0) {
			wakeChunksPerTick = 0;
			fixes.add("wakeChunksPerTick raised to 0");
		}
		if (waterDistance < 0) {
			waterDistance = 0;
			fixes.add("waterDistance raised to 0");
		}
		if (lavaDistance < 0) {
			lavaDistance = 0;
			fixes.add("lavaDistance raised to 0");
		}
		if (parkedIntervalTicks < 1) {
			parkedIntervalTicks = 1;
			fixes.add("parkedIntervalTicks raised to 1");
		}
		if (parkedCellsPerTick < 0) {
			parkedCellsPerTick = 0;
			fixes.add("parkedCellsPerTick raised to 0");
		}
		if (parkedCellsPerTick > 4096) {
			parkedCellsPerTick = 4096;
			fixes.add("parkedCellsPerTick limited to 4096");
		}
		if (!fixes.isEmpty()) {
			validateFixes = fixes;
		}
	}

	/** Human readable notes about values that had to be corrected during {@link #validate()}. */
	public transient List<String> validateFixes = List.of();

	/** Converts this configuration into the immutable settings the solver uses. */
	public SolverSettings toSolverSettings() {
		return new SolverSettings(8, cellUpdatesPerStep, pressure, pressureMaxCells,
				pressureWorkBudget, viscosity);
	}

	/** Distance, in blocks, beyond which fluid of this kind is parked. 0 disables parking for it. */
	public int distanceFor(dev.archrealm.fluids.sim.FluidKind kind) {
		int distance = kind != null && kind.isLava() ? lavaDistance : waterDistance;
		if (distance <= 0) {
			return 0;
		}
		return simulationRadius > 0 ? Math.min(distance, simulationRadius) : distance;
	}

	/** Milliseconds between two simulation steps, derived from {@link #stepsPerSecond}. */
	public int stepIntervalTicks() {
		return Math.max(1, 20 / stepsPerSecond);
	}
}
