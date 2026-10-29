package dev.archrealm.fluids.world;

import dev.archrealm.fluids.config.FluidConfig;
import dev.archrealm.fluids.sim.ActiveCells;
import dev.archrealm.fluids.sim.FluidKind;
import dev.archrealm.fluids.sim.Pos;
import net.minecraft.server.level.ServerLevel;

/**
 * Cheap block by block flow for fluid that nobody is looking at.
 *
 * <p>Fluid far away from every player is not worth a full pressure solve, but it must not freeze
 * either: vanilla-like waterfalls and rivers have to keep running while the player is elsewhere. This
 * pass therefore applies exactly one rule set — fall down if possible, otherwise creep sideways to a
 * clearly emptier neighbour — to a small number of cells per tick. It is deterministic, it conserves
 * volume and it costs a fixed, tiny amount of time no matter how much parked fluid exists.
 */
public final class ParkedFlow {
	private static final int MAX_PARKED = 200_000;
	private static final int[] DX = {0, 0, 1, -1};
	private static final int[] DZ = {1, -1, 0, 0};

	private ParkedFlow() {
	}

	/** Budget of cells that may move while parked, per pass. */
	public static int budget(FluidConfig config) {
		return Math.max(0, config.parkedCellsPerTick);
	}

	public static int maxParked() {
		return MAX_PARKED;
	}

	/**
	 * Advances up to {@code config.parkedCellsPerTick} parked cells. Returns the number of cells that
	 * actually moved, which is what the {@code /arfluids stats} command reports.
	 */
	public static int tick(ServerLevel level, ActiveCells parked, LevelFluidGrid grid, FluidConfig config) {
		if (!config.parkedFlow || parked.isEmpty()) {
			return 0;
		}
		int budget = budget(config);
		if (budget <= 0) {
			return 0;
		}
		long[] buffer = new long[Math.min(parked.size(), budget)];
		int count = parked.copyInto(buffer, buffer.length);
		int moves = 0;
		for (int i = 0; i < count; i++) {
			long cell = buffer[i];
			int x = Pos.x(cell);
			int y = Pos.y(cell);
			int z = Pos.z(cell);
			if (!grid.isLoaded(x, y, z)) {
				continue;
			}
			int units = grid.fluidUnits(x, y, z);
			if (units <= 0) {
				parked.remove(cell);
				continue;
			}
			FluidKind kind = grid.fluidKind(x, y, z);
			if (move(grid, x, y, z, kind, units)) {
				moves++;
				if (grid.fluidUnits(x, y, z) <= 0) {
					parked.remove(cell);
				}
			}
		}
		return moves;
	}

	private static boolean move(LevelFluidGrid grid, int x, int y, int z, FluidKind kind, int units) {
		int below = grid.fluidUnits(x, y - 1, z);
		if (grid.isLoaded(x, y - 1, z) && grid.canFlowInto(x, y - 1, z, kind) && below < RealisticFluid.FULL) {
			grid.setFluid(x, y - 1, z, kind, Math.min(RealisticFluid.FULL, below + 1));
			grid.setFluid(x, y, z, kind, units - 1);
			return true;
		}
		int bestX = 0;
		int bestY = 0;
		int bestZ = 0;
		int bestUnits = units;
		for (int i = 0; i < DX.length; i++) {
			int nx = x + DX[i];
			int nz = z + DZ[i];
			if (!grid.isLoaded(nx, y, nz) || !grid.canFlowInto(nx, y, nz, kind)) {
				continue;
			}
			int neighbour = grid.fluidUnits(nx, y, nz);
			// Only creep towards cells that are clearly emptier: this makes distant rivers flow
			// downhill and fill holes without the endless equalisation churn of a full solve.
			if (neighbour + 2 <= bestUnits) {
				bestUnits = neighbour;
				bestX = nx;
				bestY = y;
				bestZ = nz;
			}
		}
		if (bestUnits >= units) {
			return false;
		}
		grid.setFluid(bestX, bestY, bestZ, kind, bestUnits + 1);
		grid.setFluid(x, y, z, kind, units - 1);
		return true;
	}
}
