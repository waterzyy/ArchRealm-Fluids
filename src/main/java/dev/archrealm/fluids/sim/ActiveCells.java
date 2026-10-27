package dev.archrealm.fluids.sim;

import java.util.Arrays;

/**
 * Allocation-free hash set of packed block positions used as the solver's work queue.
 *
 * <p>Open addressing with linear probing and tombstones. Every operation is O(1) amortised and no
 * boxing occurs, which matters because the queue is touched thousands of times per tick.
 */
public final class ActiveCells {
	private static final byte FREE = 0;
	private static final byte USED = 1;
	private static final byte TOMB = 2;

	private long[] keys;
	private byte[] states;
	private int mask;
	private int size;
	private int usedAndTomb;

	public ActiveCells() {
		this(256);
	}

	public ActiveCells(int expected) {
		int capacity = Integer.highestOneBit(Math.max(8, expected) * 2 - 1) << 1;
		this.keys = new long[capacity];
		this.states = new byte[capacity];
		this.mask = capacity - 1;
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public void clear() {
		if (size != 0 || usedAndTomb != 0) {
			Arrays.fill(states, FREE);
			size = 0;
			usedAndTomb = 0;
		}
	}

	public boolean contains(long packed) {
		int slot = hash(packed) & mask;
		while (true) {
			byte state = states[slot];
			if (state == FREE) {
				return false;
			}
			if (state == USED && keys[slot] == packed) {
				return true;
			}
			slot = (slot + 1) & mask;
		}
	}

	public boolean add(long packed) {
		if (size * 4 >= keys.length * 3) {
			rehash(keys.length * 2);
		}
		return insert(packed);
	}

	public boolean remove(long packed) {
		int slot = hash(packed) & mask;
		while (true) {
			byte state = states[slot];
			if (state == FREE) {
				return false;
			}
			if (state == USED && keys[slot] == packed) {
				states[slot] = TOMB;
				size--;
				return true;
			}
			slot = (slot + 1) & mask;
		}
	}

	private boolean insert(long packed) {
		int slot = hash(packed) & mask;
		int firstTomb = -1;
		while (true) {
			byte state = states[slot];
			if (state == FREE) {
				int target = firstTomb >= 0 ? firstTomb : slot;
				if (firstTomb < 0) {
					usedAndTomb++;
				}
				keys[target] = packed;
				states[target] = USED;
				size++;
				return true;
			}
			if (state == USED && keys[slot] == packed) {
				return false;
			}
			if (state == TOMB && firstTomb < 0) {
				firstTomb = slot;
			}
			slot = (slot + 1) & mask;
		}
	}

	/**
	 * Copies up to {@code max} entries into {@code dst} without removing them and returns how many
	 * were written. The caller processes the returned slice and calls {@link #remove} for cells that
	 * settled.
	 */
	public int copyInto(long[] dst, int max) {
		int written = 0;
		for (int i = 0; i < states.length && written < max; i++) {
			if (states[i] == USED) {
				dst[written++] = keys[i];
			}
		}
		return written;
	}

	private void rehash(int capacity) {
		long[] oldKeys = keys;
		byte[] oldStates = states;
		keys = new long[capacity];
		states = new byte[capacity];
		mask = capacity - 1;
		size = 0;
		usedAndTomb = 0;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldStates[i] == USED) {
				insert(oldKeys[i]);
			}
		}
	}

	private static int hash(long value) {
		// Finaliser from splitmix64: cheap and spreads the packed coordinate bits well.
		long h = value * 0x9E3779B97F4A7C15L;
		h ^= h >>> 32;
		return (int) h;
	}

	@Override
	public String toString() {
		return "ActiveCells[size=" + size + ", capacity=" + keys.length + "]";
	}
}
