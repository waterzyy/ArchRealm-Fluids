package dev.archrealm.fluids.sim;

/**
 * The kinds of fluid that ArchRealm Fluids simulates.
 *
 * <p>The numeric ordering is significant: {@link #WATER} moves faster than {@link #LAVA}, and the
 * ordinary {@code ordinal()} comparison is used to pick the dominant fluid when two bodies meet.
 */
public enum FluidKind {
	/** Ordinary water: fast, thin, extinguishes fire, floods caves. */
	WATER(4, 8, 1),
	/** Lava: thick, slow, glowing, sets flammable blocks on fire. */
	LAVA(1, 2, 4);

	private final int horizontalRate;
	private final int verticalRate;
	private final int flowDelay;

	FluidKind(int horizontalRate, int verticalRate, int flowDelay) {
		this.horizontalRate = horizontalRate;
		this.verticalRate = verticalRate;
		this.flowDelay = flowDelay;
	}

	/** Units a full cell of this fluid can move sideways in a single simulation step. */
	public int horizontalRate() {
		return horizontalRate;
	}

	/** Units this fluid can move vertically in a single simulation step. */
	public int verticalRate() {
		return verticalRate;
	}

	/** How many simulation steps this fluid waits between movements (viscosity). */
	public int flowDelay() {
		return flowDelay;
	}

	public boolean isWater() {
		return this == WATER;
	}

	public boolean isLava() {
		return this == LAVA;
	}
}
