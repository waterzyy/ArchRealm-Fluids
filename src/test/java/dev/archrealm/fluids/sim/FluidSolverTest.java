package dev.archrealm.fluids.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/** Behavioural tests for the physics core. These run without Minecraft, in milliseconds. */
class FluidSolverTest {
	private static final SolverSettings SETTINGS = SolverSettings.DEFAULT;

	private static void run(FluidSolver solver, FluidGrid grid, ActiveCells active, int steps) {
		for (int i = 1; i <= steps; i++) {
			solver.step(grid, active, SETTINGS, i);
		}
	}

	@Test
	void waterFallsToTheFloor() {
		TestGrid grid = new TestGrid().floor(-2, 2, -2, 2, 0);
		grid.fill(0, 6, 0, FluidKind.WATER, 4);
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		solver.activate(active, 0, 6, 0);

		run(solver, grid, active, 40);

		assertEquals(0, grid.fluidUnits(0, 6, 0), "the source cell is empty again");
		assertEquals(4, grid.total(FluidKind.WATER), "volume is conserved");
		assertTrue(grid.fluidCells(FluidKind.WATER).stream().allMatch(p -> Pos.y(p) == 1),
				"everything ends up on the floor layer");
	}

	@Test
	void waterSpreadsIntoAThinLayer() {
		TestGrid grid = new TestGrid().floor(-8, 8, -8, 8, 0);
		grid.fill(0, 1, 0, FluidKind.WATER, 8);
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		solver.activate(active, 0, 1, 0);

		run(solver, grid, active, 400);

		assertEquals(8, grid.total(FluidKind.WATER), "volume is conserved while spreading");
		long cells = grid.fluidCells(FluidKind.WATER).size();
		assertTrue(cells >= 6, "a bucket spreads over at least six cells, got " + cells);
		for (long packed : grid.fluidCells(FluidKind.WATER)) {
			assertEquals(1, Pos.y(packed), "the puddle stays on the floor layer");
		}
	}

	@Test
	void flatPoolDoesNotFountain() {
		// A 7x7 basin, one block deep, walled in on the sides, open to the sky.
		TestGrid grid = new TestGrid()
				.floor(-4, 4, -4, 4, 0)
				.wall(-4, -4, -4, 4, 1, 1)
				.wall(4, 4, -4, 4, 1, 1)
				.wall(-4, 4, -4, -4, 1, 1)
				.wall(-4, 4, 4, 4, 1, 1);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				grid.fill(x, 1, z, FluidKind.WATER, 8);
			}
		}
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				solver.activate(active, x, 1, z);
			}
		}
		int before = grid.total(FluidKind.WATER);

		run(solver, grid, active, 60);

		assertEquals(before, grid.total(FluidKind.WATER), "the pool keeps its volume");
		for (long packed : grid.fluidCells(FluidKind.WATER)) {
			assertEquals(1, Pos.y(packed), "still water never climbs out of the pool");
		}
		assertTrue(active.size() < 128, "the settled pool goes dormant, size=" + active.size());
	}

	@Test
	void wellRisesToTheLevelOfThePool() {
		// A 1x1 shaft next to a full pool, connected at the bottom: hydrostatic pressure must push
		// water up the shaft until both surfaces are level.
		TestGrid grid = new TestGrid()
				.floor(-4, 4, -2, 2, 0)
				.wall(-4, 4, -3, -3, 1, 12)
				.wall(-4, 4, 3, 3, 1, 12)
				.wall(-4, -4, -2, 2, 1, 12)
				.wall(4, 4, -2, 2, 1, 12)
				.wall(2, 2, -2, 2, 1, 1);
		for (int y = 1; y <= 6; y++) {
			for (int x = -3; x <= 1; x++) {
				for (int z = -2; z <= 2; z++) {
					grid.fill(x, y, z, FluidKind.WATER, 8);
				}
			}
		}
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		for (long packed : grid.fluidCells(FluidKind.WATER)) {
			solver.activate(active, Pos.x(packed), Pos.y(packed), Pos.z(packed));
		}
		int total = grid.total(FluidKind.WATER);

		run(solver, grid, active, 3000);

		assertEquals(total, grid.total(FluidKind.WATER), "volume is conserved while the well fills");
		assertTrue(grid.amount(3, 1, 0) > 0, "water reaches the shaft");
		assertTrue(grid.amount(3, 3, 0) > 0, "pressure lifts water several blocks up the shaft");
		int shaftTop = topOf(grid, 3, 0);
		int poolTop = topOf(grid, 0, 0);
		assertTrue(Math.abs(shaftTop - poolTop) <= 1,
				"the shaft surface settles at the pool level (" + shaftTop + " vs " + poolTop + ")");
	}

	private static int topOf(TestGrid grid, int x, int z) {
		int top = 0;
		for (int y = -64; y < 320; y++) {
			if (grid.amount(x, y, z) > 0) {
				top = y;
			}
		}
		return top;
	}

	@Test
	void noCellEverExceedsCapacity() {
		TestGrid grid = new TestGrid().floor(-4, 4, -4, 4, 0);
		for (int y = 1; y <= 6; y++) {
			grid.fill(0, y, 0, FluidKind.WATER, 4);
		}
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		solver.activate(active, 0, 6, 0);
		for (int step = 1; step <= 400; step++) {
			solver.step(grid, active, SETTINGS, step);
			for (long packed : grid.fluidCells(FluidKind.WATER)) {
				int amount = grid.amount(Pos.x(packed), Pos.y(packed), Pos.z(packed));
				assertTrue(amount <= SETTINGS.maxUnits(),
						"overflowing cell at " + Pos.x(packed) + "," + Pos.y(packed) + "," + Pos.z(packed)
								+ " with " + amount + " units on step " + step);
				assertTrue(amount > 0, "empty cells are removed from the grid");
			}
		}
	}

	@Test
	void lavaIsMoreViscousThanWater() {
		TestGrid waterGrid = new TestGrid().floor(-8, 8, -8, 8, 0);
		waterGrid.fill(0, 1, 0, FluidKind.WATER, 8);
		TestGrid lavaGrid = new TestGrid().floor(-8, 8, -8, 8, 0);
		lavaGrid.fill(0, 1, 0, FluidKind.LAVA, 8);

		FluidSolver waterSolver = new FluidSolver();
		FluidSolver lavaSolver = new FluidSolver();
		ActiveCells waterActive = new ActiveCells();
		ActiveCells lavaActive = new ActiveCells();
		waterSolver.activate(waterActive, 0, 1, 0);
		lavaSolver.activate(lavaActive, 0, 1, 0);

		run(waterSolver, waterGrid, waterActive, 40);
		run(lavaSolver, lavaGrid, lavaActive, 40);

		int waterSpread = waterGrid.fluidCells(FluidKind.WATER).size();
		int lavaSpread = lavaGrid.fluidCells(FluidKind.LAVA).size();
		assertTrue(waterSpread > lavaSpread,
				"water spreads faster (" + waterSpread + ") than lava (" + lavaSpread + ")");
		assertEquals(8, lavaGrid.total(FluidKind.LAVA), "lava volume is conserved");
	}

	@Test
	void volumeIsConservedWithRandomTerrain() {
		Random random = new Random(0xC0FFEEL);
		TestGrid grid = new TestGrid();
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				grid.solid(x, 0, z);
				for (int y = 1; y <= 5; y++) {
					if (random.nextInt(5) == 0) {
						grid.solid(x, y, z);
					}
				}
			}
		}
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		for (int i = 0; i < 40; i++) {
			int x = random.nextInt(13) - 6;
			int z = random.nextInt(13) - 6;
			int y = 1 + random.nextInt(6);
			if (grid.canFlowInto(x, y, z, FluidKind.WATER)) {
				grid.fill(x, y, z, FluidKind.WATER, 8);
				solver.activate(active, x, y, z);
			}
		}
		int total = grid.total(FluidKind.WATER);
		assertTrue(total > 0, "the scenario contains water");

		run(solver, grid, active, 1200);

		assertEquals(total, grid.total(FluidKind.WATER), "no water is lost or duplicated");
	}

	@Test
	void budgetCapsWorkPerStep() {
		TestGrid grid = new TestGrid().floor(-20, 20, -20, 20, 0);
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		for (int x = -20; x <= 20; x++) {
			for (int z = -20; z <= 20; z++) {
				grid.fill(x, 1, z, FluidKind.WATER, 8);
				solver.activate(active, x, 1, z);
			}
		}
		SolverSettings small = SETTINGS.withMaxCellUpdatesPerStep(16);

		solver.step(grid, active, small, 1);

		assertTrue(solver.stats().visited() <= 16,
				"only the budgeted number of cells is visited, got " + solver.stats().visited());
	}

	@Test
	void simulationIsDeterministic() {
		int[] first = simulateOnce();
		int[] second = simulateOnce();
		assertEquals(first.length, second.length);
		for (int i = 0; i < first.length; i++) {
			assertEquals(first[i], second[i], "same input produces the same result at index " + i);
		}
	}

	private static int[] simulateOnce() {
		TestGrid grid = new TestGrid().floor(-4, 4, -4, 4, 0);
		for (int i = 0; i < 10; i++) {
			grid.fill(i % 5 - 2, 3, i % 3 - 1, FluidKind.WATER, 8);
		}
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		solver.activate(active, 0, 3, 0);
		run(solver, grid, active, 200);
		return grid.fluidCells(FluidKind.WATER).stream().mapToInt(Long::intValue).sorted().toArray();
	}

	@Test
	void unloadedCellsAreSkipped() {
		TestGrid grid = new TestGrid() {
			@Override
			public boolean isLoaded(int x, int y, int z) {
				return x >= 0 && super.isLoaded(x, y, z);
			}
		};
		grid.fill(0, 3, 0, FluidKind.WATER, 8);
		FluidSolver solver = new FluidSolver();
		ActiveCells active = new ActiveCells();
		solver.activate(active, 0, 3, 0);

		run(solver, grid, active, 50);

		assertTrue(grid.fluidCells(FluidKind.WATER).stream().allMatch(p -> Pos.x(p) >= 0),
				"fluid never flows into unloaded chunks");
	}
}
