package dev.archrealm.fluids.sim;

/**
 * A read/write view of the fluid world used by {@link FluidSolver}.
 *
 * <p>The interface is deliberately free of any Minecraft type so the whole simulation core can be
 * unit tested on a plain in-memory grid. The Minecraft adapter lives in
 * {@code dev.archrealm.fluids.world.LevelFluidGrid}.
 *
 * <p>Coordinates are world block coordinates and {@code y} grows upwards. Implementations must be
 * cheap: the solver calls these methods thousands of times per tick.
 */
public interface FluidGrid {
	/** Amount of fluid in the cell, in units. {@code 0} means the cell is empty. */
	int fluidUnits(int x, int y, int z);

	/** Fluid kind stored in the cell, never {@code null}; use {@code units == 0} to detect empty cells. */
	FluidKind fluidKind(int x, int y, int z);

	/** Whether the chunk containing the position is loaded and may be modified. */
	boolean isLoaded(int x, int y, int z);

	/**
	 * Writes fluid into a cell. {@code units == 0} removes the fluid from the cell.
	 *
	 * <p>Implementations must accept any value from {@code 0} to the configured maximum and must not
	 * trigger cascading vanilla neighbour updates; the solver activates the neighbourhood itself.
	 */
	void setFluid(int x, int y, int z, FluidKind kind, int units);

	/**
	 * Whether a fluid of {@code kind} may enter the cell at all.
	 *
	 * <p>Usually this is "the block is air or replaceable". A cell holding the other fluid kind is
	 * never enterable; the reaction system handles that case instead.
	 */
	boolean canFlowInto(int x, int y, int z, FluidKind kind);
}
