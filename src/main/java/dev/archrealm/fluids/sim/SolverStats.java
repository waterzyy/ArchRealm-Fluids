package dev.archrealm.fluids.sim;

/**
 * Counters produced by one {@link FluidSolver#step} invocation. Reused between steps to avoid
 * allocation; read it right after the step returns.
 */
public final class SolverStats {
	private long steps;
	private int visited;
	private int moves;
	private int unitsMoved;
	private int deactivated;
	private int budgetSkipped;
	private int pressureFloods;

	public void resetStep() {
		visited = 0;
		moves = 0;
		unitsMoved = 0;
		deactivated = 0;
		budgetSkipped = 0;
		pressureFloods = 0;
	}

	void countStep() {
		steps++;
	}

	void countVisited() {
		visited++;
	}

	void countMove(int units) {
		moves++;
		unitsMoved += units;
	}

	void countDeactivated() {
		deactivated++;
	}

	void countBudgetSkipped() {
		budgetSkipped++;
	}

	void countPressureFlood() {
		pressureFloods++;
	}

	public long steps() {
		return steps;
	}

	public int visited() {
		return visited;
	}

	public int moves() {
		return moves;
	}

	public int unitsMoved() {
		return unitsMoved;
	}

	public int deactivated() {
		return deactivated;
	}

	public int budgetSkipped() {
		return budgetSkipped;
	}

	public int pressureFloods() {
		return pressureFloods;
	}

	@Override
	public String toString() {
		return "SolverStats[steps=" + steps + ", visited=" + visited + ", moves=" + moves
				+ ", units=" + unitsMoved + ", deactivated=" + deactivated
				+ ", skipped=" + budgetSkipped + ", floods=" + pressureFloods + "]";
	}
}
