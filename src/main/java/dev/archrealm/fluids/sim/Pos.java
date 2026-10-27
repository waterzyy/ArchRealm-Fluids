package dev.archrealm.fluids.sim;

/**
 * Bit packing for block positions used by the simulation hot paths.
 *
 * <p>Layout: {@code y (12 bits, offset 2048) | z (26 bits, signed) | x (26 bits, signed)}. Because
 * {@code y} occupies the most significant bits, sorting packed longs sorts positions bottom-up,
 * which is exactly the order the solver needs to keep columns stable and to avoid "bouncing" water.
 */
public final class Pos {
	/** Added to {@code y} before packing so the negative world range survives the shift. */
	public static final int Y_OFFSET = 2048;
	private static final int COORD_BITS = 26;
	private static final long COORD_MASK = (1L << COORD_BITS) - 1L;
	private static final int SIGN = 1 << (COORD_BITS - 1);
	private static final long COORD_RANGE = 1L << COORD_BITS;

	private Pos() {
	}

	public static long pack(int x, int y, int z) {
		return ((long) (y + Y_OFFSET) << 52) | ((long) (z & COORD_MASK) << COORD_BITS) | (x & COORD_MASK);
	}

	public static int x(long packed) {
		return signExtend((int) (packed & COORD_MASK));
	}

	public static int z(long packed) {
		return signExtend((int) ((packed >>> COORD_BITS) & COORD_MASK));
	}

	public static int y(long packed) {
		return (int) (packed >>> 52) - Y_OFFSET;
	}

	private static int signExtend(int value) {
		if ((value & SIGN) != 0) {
			value -= (int) COORD_RANGE;
		}
		return value;
	}
}
