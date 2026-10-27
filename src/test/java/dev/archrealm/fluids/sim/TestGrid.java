package dev.archrealm.fluids.sim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory {@link FluidGrid} used by the unit tests. */
class TestGrid implements FluidGrid {
	private final Map<Long, Integer> units = new HashMap<>();
	private final Map<Long, FluidKind> kinds = new HashMap<>();
	private final Set<Long> solids = new HashSet<>();

	TestGrid solid(int x, int y, int z) {
		solids.add(Pos.pack(x, y, z));
		return this;
	}

	TestGrid fill(int x, int y, int z, FluidKind kind, int amount) {
		long key = Pos.pack(x, y, z);
		units.put(key, amount);
		kinds.put(key, kind);
		return this;
	}

	TestGrid floor(int fromX, int toX, int fromZ, int toZ, int y) {
		for (int x = fromX; x <= toX; x++) {
			for (int z = fromZ; z <= toZ; z++) {
				solid(x, y, z);
			}
		}
		return this;
	}

	TestGrid wall(int fromX, int toX, int fromZ, int toZ, int fromY, int toY) {
		for (int x = fromX; x <= toX; x++) {
			for (int z = fromZ; z <= toZ; z++) {
				for (int y = fromY; y <= toY; y++) {
					solid(x, y, z);
				}
			}
		}
		return this;
	}

	int amount(int x, int y, int z) {
		return units.getOrDefault(Pos.pack(x, y, z), 0);
	}

	int total(FluidKind kind) {
		int sum = 0;
		for (Map.Entry<Long, Integer> entry : units.entrySet()) {
			if (kinds.get(entry.getKey()) == kind) {
				sum += entry.getValue();
			}
		}
		return sum;
	}

	Set<Long> fluidCells(FluidKind kind) {
		Set<Long> result = new HashSet<>();
		for (Map.Entry<Long, FluidKind> entry : kinds.entrySet()) {
			if (entry.getValue() == kind && units.getOrDefault(entry.getKey(), 0) > 0) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	@Override
	public int fluidUnits(int x, int y, int z) {
		return units.getOrDefault(Pos.pack(x, y, z), 0);
	}

	@Override
	public FluidKind fluidKind(int x, int y, int z) {
		return kinds.getOrDefault(Pos.pack(x, y, z), FluidKind.WATER);
	}

	@Override
	public boolean isLoaded(int x, int y, int z) {
		return y > -64 && y < 320;
	}

	@Override
	public void setFluid(int x, int y, int z, FluidKind kind, int amount) {
		long key = Pos.pack(x, y, z);
		if (amount <= 0) {
			units.remove(key);
			kinds.remove(key);
			return;
		}
		units.put(key, amount);
		kinds.put(key, kind);
	}

	@Override
	public boolean canFlowInto(int x, int y, int z, FluidKind kind) {
		if (solids.contains(Pos.pack(x, y, z))) {
			return false;
		}
		Integer existing = units.get(Pos.pack(x, y, z));
		if (existing == null || existing <= 0) {
			return true;
		}
		return kinds.get(Pos.pack(x, y, z)) == kind;
	}
}
