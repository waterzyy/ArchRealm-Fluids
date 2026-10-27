package dev.archrealm.fluids.sim;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic, volume-conserving fluid solver.
 *
 * <p>Every cell stores a discrete amount of fluid between {@code 0} and {@code maxUnits}. A step
 * visits the active cells bottom-up and lets each of them perform at most one of:
 *
 * <ol>
 *   <li><b>fall</b> - the content drops into the cell below while there is room (gravity);</li>
 *   <li><b>rise</b> - a full cell with hydrostatic head pulls a unit out of the highest surface of
 *       its body and pushes it upwards. This is what makes communicating vessels, U-tubes and
 *       "water finds its level" work;</li>
 *   <li><b>spread</b> - a cell moves at most {@code diff / 2} units sideways into a lower
 *       neighbour, which converges to a flat surface without oscillating.</li>
 * </ol>
 *
 * <p>Volume is never created or destroyed: every move removes as many units from the source as it
 * adds to the destination.
 *
 * <p>Not thread safe; one instance belongs to one world and is used from the server thread only.
 */
public final class FluidSolver {
	private static final int[] DX = {0, 0, 1, -1, 0, 0};
	private static final int[] DZ = {1, -1, 0, 0, 0, 0};
	private static final int[] DY = {0, 0, 0, 0, 1, -1};
	private static final int MAX_PUSHES_PER_REGION = 32;
	/**
	 * Minimum hydrostatic head (in units, 8 units = one block) required before water is pushed
	 * upwards. Without it a nearly level surface would keep trading single units forever.
	 */
	private static final int HEAD_THRESHOLD = 2;
	/** Steps with almost no activity after which a body is considered settled and goes dormant. */
	private static final int LOW_ACTIVITY_STEPS = 8;

	private final SolverStats stats = new SolverStats();
	private final Map<Long, Region> regionCache = new HashMap<>();

	private long[] buffer = new long[1024];
	private long[] queue = new long[1024];
	private int pressureWork;
	private int lowActivitySteps;
	private ActiveCells active;

	public SolverStats stats() {
		return stats;
	}

	/** Runs a single simulation step over (at most) the configured number of active cells. */
	public void step(FluidGrid grid, ActiveCells activeCells, SolverSettings settings, long stepIndex) {
		stats.resetStep();
		stats.countStep();
		if (activeCells.isEmpty()) {
			return;
		}
		int budget = settings.maxCellUpdatesPerStep();
		if (buffer.length < budget) {
			buffer = new long[budget];
		}
		int count = activeCells.copyInto(buffer, budget);
		if (count == 0) {
			return;
		}
		if (activeCells.size() > count) {
			stats.countBudgetSkipped();
		}

		active = activeCells;
		regionCache.clear();
		pressureWork = 0;
		// Bottom-up order keeps columns stable: a donor is always refilled before the cell above it
		// gets a chance to fall back, so water never visibly bounces.
		Arrays.sort(buffer, 0, count);

		for (int i = 0; i < count; i++) {
			processCell(grid, settings, stepIndex, buffer[i]);
		}

		active = null;
		boolean idle = stats.moves() == 0
				|| (stats.moves() * 16L <= stats.visited() && ++lowActivitySteps >= LOW_ACTIVITY_STEPS);
		if (idle && activeCells.size() <= count) {
			// The body is at rest (or only trades a few units back and forth): go dormant. Any block
			// update near the fluid wakes it again, so nothing is lost and idle worlds cost nothing.
			activeCells.clear();
			lowActivitySteps = 0;
		} else if (!idle) {
			lowActivitySteps = 0;
		}
	}

	/** Adds a position and its six neighbours to the active set of the solver. */
	public void activate(ActiveCells activeCells, int x, int y, int z) {
		activeCells.add(Pos.pack(x, y, z));
		for (int i = 0; i < 6; i++) {
			activeCells.add(Pos.pack(x + DX[i], y + DY[i], z + DZ[i]));
		}
	}

	private void processCell(FluidGrid grid, SolverSettings settings, long stepIndex, long packed) {
		int x = Pos.x(packed);
		int y = Pos.y(packed);
		int z = Pos.z(packed);

		if (!grid.isLoaded(x, y, z)) {
			active.remove(packed);
			stats.countDeactivated();
			return;
		}
		int units = grid.fluidUnits(x, y, z);
		if (units <= 0) {
			active.remove(packed);
			stats.countDeactivated();
			return;
		}
		FluidKind kind = grid.fluidKind(x, y, z);
		stats.countVisited();

		if (!settings.ticksOnStep(kind, stepIndex)) {
			// Viscous fluid: still active, just not moving on this step.
			return;
		}
		if (tryFall(grid, settings, x, y, z, kind, units)) {
			return;
		}
		if (settings.pressureEnabled() && units >= settings.maxUnits()
				&& tryRise(grid, settings, x, y, z, kind, packed)) {
			return;
		}
		trySpread(grid, settings, x, y, z, kind, units);
	}

	private boolean tryFall(FluidGrid grid, SolverSettings settings, int x, int y, int z, FluidKind kind, int units) {
		int belowY = y - 1;
		if (!grid.isLoaded(x, belowY, z) || !grid.canFlowInto(x, belowY, z, kind)) {
			return false;
		}
		int belowUnits = grid.fluidUnits(x, belowY, z);
		int room = settings.maxUnits() - belowUnits;
		if (room <= 0) {
			return false;
		}
		int amount = Math.min(Math.min(units, room), settings.verticalRate(kind));
		if (amount <= 0) {
			return false;
		}
		move(grid, x, y, z, x, belowY, z, kind, amount);
		return true;
	}

	private boolean tryRise(FluidGrid grid, SolverSettings settings, int x, int y, int z, FluidKind kind, long packed) {
		int aboveY = y + 1;
		if (!grid.isLoaded(x, aboveY, z) || !grid.canFlowInto(x, aboveY, z, kind)) {
			return false;
		}
		int aboveUnits = grid.fluidUnits(x, aboveY, z);
		int room = settings.maxUnits() - aboveUnits;
		if (room <= 0) {
			return false;
		}
		Region region = regionOf(grid, settings, x, y, z, kind, packed);
		if (region == null) {
			return false;
		}
		if (region.maxTopUnits <= (y + 1) * settings.maxUnits() + HEAD_THRESHOLD) {
			// This cell already sits at (or barely below) the surface of its body: no head, and no
			// fountain. The threshold stops two neighbouring columns from trading one unit forever.
			return false;
		}
		long donor = region.donor;
		long target = Pos.pack(x, aboveY, z);
		if (donor == packed || donor == target || region.pushes >= MAX_PUSHES_PER_REGION) {
			// Pulling a unit out of the very cell we are filling would duplicate it: the surface
			// of the body is the cell above, which means the body is already at its level here.
			return false;
		}
		int donorX = Pos.x(donor);
		int donorY = Pos.y(donor);
		int donorZ = Pos.z(donor);
		if (!grid.isLoaded(donorX, donorY, donorZ) || grid.fluidKind(donorX, donorY, donorZ) != kind
				|| grid.fluidUnits(donorX, donorY, donorZ) <= 0
				|| !grid.canFlowInto(donorX, donorY + 1, donorZ, kind)) {
			// The cached surface is stale (drained, covered or unloaded): recompute next step.
			region.stale = true;
			return false;
		}
		int amount = Math.min(room, 1);
		int donorUnits = grid.fluidUnits(donorX, donorY, donorZ);
		move(grid, donorX, donorY, donorZ, x, aboveY, z, kind, amount);
		region.pushes++;
		// The donor lost a unit, so the highest point of the body dropped by one unit as well. Keeping
		// the cached value in sync is what makes repeated pushes inside one step converge instead of
		// overshooting and forcing water back down again.
		region.maxTopUnits--;
		if (donorUnits - amount <= 0) {
			region.stale = true;
		}
		return true;
	}

	private void trySpread(FluidGrid grid, SolverSettings settings, int x, int y, int z, FluidKind kind, int units) {
		int rate = settings.horizontalRate(kind);
		int start = Math.floorMod(x * 31 + z * 17 + y * 7, 4);
		for (int i = 0; i < 4; i++) {
			int dir = (start + i) & 3;
			int nx = x + DX[dir];
			int nz = z + DZ[dir];
			if (!grid.isLoaded(nx, y, nz) || !grid.canFlowInto(nx, y, nz, kind)) {
				continue;
			}
			int targetUnits = grid.fluidUnits(nx, y, nz);
			int diff = units - targetUnits;
			if (diff < 2) {
				continue;
			}
			int amount = Math.min(rate, diff / 2);
			if (amount <= 0) {
				continue;
			}
			move(grid, x, y, z, nx, y, nz, kind, amount);
			return;
		}
	}

	private void move(FluidGrid grid, int fromX, int fromY, int fromZ, int toX, int toY, int toZ,
					  FluidKind kind, int amount) {
		if (fromX == toX && fromY == toY && fromZ == toZ) {
			// Defensive: a self-move must never change the cell, otherwise volume would be created.
			return;
		}
		int fromUnits = grid.fluidUnits(fromX, fromY, fromZ);
		int moved = Math.min(amount, fromUnits);
		if (moved <= 0) {
			return;
		}
		int toUnits = grid.fluidUnits(toX, toY, toZ);
		grid.setFluid(fromX, fromY, fromZ, kind, fromUnits - moved);
		grid.setFluid(toX, toY, toZ, kind, toUnits + moved);
		stats.countMove(moved);
		if (active != null) {
			activate(active, fromX, fromY, fromZ);
			activate(active, toX, toY, toZ);
		}
	}

	private Region regionOf(FluidGrid grid, SolverSettings settings, int x, int y, int z, FluidKind kind, long packed) {
		Region cached = regionCache.get(packed);
		if (cached != null && !cached.stale) {
			return cached.capped ? null : cached;
		}
		if (pressureWork >= settings.pressureWorkBudget()) {
			return null;
		}
		Region region = new Region();
		int head = 0;
		int tail = 1;
		queue[0] = packed;
		regionCache.put(packed, region);
		int maxCells = settings.pressureMaxCells();
		while (head < tail) {
			long current = queue[head++];
			int cx = Pos.x(current);
			int cy = Pos.y(current);
			int cz = Pos.z(current);
			int units = grid.fluidUnits(cx, cy, cz);
			if (units <= 0 || grid.fluidKind(cx, cy, cz) != kind) {
				continue;
			}
			region.cells++;
			pressureWork++;
			int topUnits = cy * settings.maxUnits() + units;
			if (topUnits > region.maxTopUnits) {
				region.maxTopUnits = topUnits;
				region.donor = current;
			}
			if (region.cells >= maxCells) {
				region.capped = true;
				break;
			}
			for (int i = 0; i < 6; i++) {
				int nx = cx + DX[i];
				int ny = cy + DY[i];
				int nz = cz + DZ[i];
				long neighbour = Pos.pack(nx, ny, nz);
				Region known = regionCache.get(neighbour);
				if (known != null) {
					if (!known.stale) {
						continue;
					}
					regionCache.remove(neighbour);
				}
				if (grid.fluidUnits(nx, ny, nz) <= 0 || grid.fluidKind(nx, ny, nz) != kind) {
					continue;
				}
				if (tail == queue.length) {
					queue = Arrays.copyOf(queue, queue.length * 2);
				}
				queue[tail++] = neighbour;
				regionCache.put(neighbour, region);
			}
		}
		if (region.maxTopUnits == Integer.MIN_VALUE) {
			region.maxTopUnits = (y + 1) * settings.maxUnits();
		}
		if (region.donor == 0L) {
			region.donor = packed;
		}
		return region.capped ? null : region;
	}

	private static final class Region {
		int maxTopUnits = Integer.MIN_VALUE;
		long donor;
		int cells;
		int pushes;
		boolean capped;
		boolean stale;
	}
}
