package dev.archrealm.fluids.world;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.config.FluidConfig;
import dev.archrealm.fluids.sim.ActiveCells;
import dev.archrealm.fluids.sim.FluidKind;
import dev.archrealm.fluids.sim.FluidSolver;
import dev.archrealm.fluids.sim.Pos;
import dev.archrealm.fluids.sim.SolverStats;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The server side heart of the mod: one simulation per dimension, stepped from the server tick.
 *
 * <p>Work is done at a fixed rate ({@code stepsPerSecond}) and each step may only touch a fixed number
 * of cells ({@code cellUpdatesPerStep}), so the cost is bounded no matter how much fluid exists. Fluid
 * that settles is removed from the queue and costs nothing until a neighbour changes again; chunks are
 * only scanned when they are loaded or explicitly woken.
 */
public final class FluidEngine {
	private static final Map<ResourceKey<Level>, WorldState> STATES = new HashMap<>();
	private static final java.util.function.Predicate<BlockState> FLUID_STATE =
			state -> state.getBlock() instanceof RealisticFluidBlock;

	private FluidEngine() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FluidEngine::onServerTick);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (!ArchRealmFluids.config().wakeOnChunkLoad) {
				return;
			}
			// Create the state on demand: chunks can load before the first server tick.
			state(level).wakeChunk(chunk.getPos());
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			WorldState state = STATES.get(level.dimension());
			if (state != null) {
				state.queue.remove(chunk.getPos());
			}
		});
	}

	// --- public API used by blocks, items and commands ---------------------

	/** Queues the cell at {@code pos} and its neighbours for the next step. */
	public static void wake(Level level, BlockPos pos) {
		if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		WorldState state = STATES.get(level.dimension());
		if (state == null) {
			return;
		}
		state.activate(serverLevel, pos);
	}

	/** Queues a whole radius of chunks (used by commands and by chunk load). */
	public static void wakeArea(ServerLevel level, BlockPos center, int radius) {
		WorldState state = state(level);
		int chunks = Math.max(1, (radius >> 4) + 1);
		ChunkPos centerChunk = ChunkPos.containing(center);
		for (int dx = -chunks; dx <= chunks; dx++) {
			for (int dz = -chunks; dz <= chunks; dz++) {
				state.wakeChunk(new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz));
			}
		}
	}

	/** Adds up to {@code units} of fluid to a cell; returns how many units were actually placed. */
	public static int pour(ServerLevel level, BlockPos pos, FluidKind kind, int units) {
		if (units <= 0 || level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
			return 0;
		}
		BlockState state = level.getBlockState(pos);
		int current = RealisticFluidBlock.unitsIn(state, kind);
		if (current <= 0) {
			if (state.getBlock() instanceof RealisticFluidBlock) {
				// Another fluid lives here; mixing would destroy volume, so refuse.
				return 0;
			}
			if (!state.canBeReplaced()) {
				return 0;
			}
		}
		int room = RealisticFluid.FULL - current;
		if (room <= 0) {
			return 0;
		}
		int placed = Math.min(room, units);
		level.setBlock(pos, RealisticFluidBlock.stateFor(kind, current + placed), Block.UPDATE_CLIENTS);
		wake(level, pos);
		return placed;
	}

	/** Removes up to {@code units} of fluid from a cell; returns how many units were removed. */
	public static int drain(ServerLevel level, BlockPos pos, FluidKind kind, int units) {
		if (units <= 0) {
			return 0;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof RealisticFluidBlock block)) {
			return 0;
		}
		if (kind != null && block.kind() != kind) {
			return 0;
		}
		int current = RealisticFluidBlock.unitsOf(state);
		int removed = Math.min(current, units);
		if (removed <= 0) {
			return 0;
		}
		int left = current - removed;
		level.setBlock(pos, left <= 0 ? Blocks.AIR.defaultBlockState()
				: RealisticFluidBlock.stateFor(block.kind(), left), Block.UPDATE_CLIENTS);
		wake(level, pos);
		return removed;
	}

	public static int unitsAt(Level level, BlockPos pos) {
		return RealisticFluidBlock.unitsOf(level.getBlockState(pos));
	}

	public static StatsSnapshot snapshot(ServerLevel level) {
		WorldState state = STATES.get(level.dimension());
		if (state == null) {
			return new StatsSnapshot(0, 0, 0, 0, 0, 0);
		}
		SolverStats stats = state.solver.stats();
		return new StatsSnapshot(state.activeCells.size(), state.parkedCells.size(), state.queue.size(),
				stats.visited(), stats.moves(), state.steps);
	}

	/** Removes all realistic fluid within {@code radius} blocks of {@code center}. */
	public static int clearArea(ServerLevel level, BlockPos center, int radius) {
		int removed = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (level.isOutsideBuildHeight(cursor) || !level.isLoaded(cursor)) {
						continue;
					}
					BlockState state = level.getBlockState(cursor);
					if (state.getBlock() instanceof RealisticFluidBlock) {
						level.setBlock(cursor.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						removed++;
					}
				}
			}
		}
		WorldState state = STATES.get(level.dimension());
		if (state != null) {
			state.activeCells.clear();
			state.parkedCells.clear();
		}
		return removed;
	}

	/** Drops all simulation state for a dimension (used by {@code /arfluids clear all}). */
	public static void reset(ServerLevel level) {
		WorldState state = STATES.get(level.dimension());
		if (state != null) {
			state.activeCells.clear();
			state.parkedCells.clear();
			state.queue.clear();
		}
	}

	// --- ticking ----------------------------------------------------------

	private static void onServerTick(MinecraftServer server) {
		FluidConfig config = ArchRealmFluids.config();
		for (ServerLevel level : server.getAllLevels()) {
			WorldState state = STATES.get(level.dimension());
			if (state == null) {
				if (!config.enabled) {
					continue;
				}
				state = state(level);
			}
			state.tick(level, config);
			if (level.getGameTime() % 100 == 0) {
				refreshPlayerArea(level, state);
			}
		}
	}

	/** Re-queues the chunks around every player, so fluid that settled is re-examined regularly. */
	private static void refreshPlayerArea(ServerLevel level, WorldState state) {
		for (var player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			ChunkPos center = ChunkPos.containing(player.blockPosition());
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					state.wakeChunk(new ChunkPos(center.x() + dx, center.z() + dz));
				}
			}
		}
	}

	private static WorldState state(ServerLevel level) {
		return STATES.computeIfAbsent(level.dimension(), key -> new WorldState(level));
	}

	/** Per dimension simulation state. */
	private static final class WorldState {
		private final FluidSolver solver = new FluidSolver();
		private final ActiveCells activeCells = new ActiveCells();
		private final ActiveCells parkedCells = new ActiveCells();
		private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
		private final LevelFluidGrid grid;
		private long steps;
		private int tickCounter;

		private WorldState(ServerLevel level) {
			this.grid = new LevelFluidGrid(level);
		}

		private void tick(ServerLevel level, FluidConfig config) {
			tickCounter++;
			scanChunks(level, config);
			int interval = Math.max(1, 20 / Math.max(1, config.stepsPerSecond));
			if (tickCounter % interval != 0) {
				return;
			}
			steps++;
			if (config.parkedFlow && tickCounter % Math.max(1, config.parkedIntervalTicks) == 0) {
				ParkedFlow.tick(level, parkedCells, grid, config);
			}
			if (tickCounter % 100 == 0) {
				repark(level, config);
			}
			if (!config.enabled || activeCells.isEmpty()) {
				return;
			}
			solver.step(grid, activeCells, config.toSolverSettings(), steps);
			applyReactions(level, config);
		}

		/**
		 * Moves fluid between the full solver and the cheap parked pass as players travel: the solver
		 * only touches what someone can see, distant water keeps flowing block by block.
		 */
		private void repark(ServerLevel level, FluidConfig config) {
			if (!config.parkedFlow) {
				restoreParked();
				return;
			}
			if (!activeCells.isEmpty()) {
				long[] buffer = new long[Math.min(activeCells.size(), 8192)];
				int count = activeCells.copyInto(buffer, buffer.length);
				for (int i = 0; i < count; i++) {
					long cell = buffer[i];
					if (parkedCells.size() >= ParkedFlow.maxParked()) {
						break;
					}
					int distance = config.distanceFor(grid.fluidKind(Pos.x(cell), Pos.y(cell), Pos.z(cell)));
					if (distance > 0 && !hasPlayerNear(level, cell, distance)) {
						activeCells.remove(cell);
						parkedCells.add(cell);
					}
				}
			}
			if (parkedCells.isEmpty()) {
				return;
			}
			long[] buffer = new long[Math.min(parkedCells.size(), 4096)];
			int count = parkedCells.copyInto(buffer, buffer.length);
			for (int i = 0; i < count; i++) {
				long cell = buffer[i];
				int distance = config.distanceFor(grid.fluidKind(Pos.x(cell), Pos.y(cell), Pos.z(cell)));
				if (distance <= 0 || hasPlayerNear(level, cell, distance * 0.9)) {
					parkedCells.remove(cell);
					activeCells.add(cell);
				}
			}
		}

		private void restoreParked() {
			if (parkedCells.isEmpty()) {
				return;
			}
			long[] buffer = new long[Math.min(parkedCells.size(), 4096)];
			int count = parkedCells.copyInto(buffer, buffer.length);
			for (int i = 0; i < count; i++) {
				activeCells.add(buffer[i]);
				parkedCells.remove(buffer[i]);
			}
		}

		private static boolean hasPlayerNear(ServerLevel level, long cell, double distance) {
			return level.getNearestPlayer(Pos.x(cell) + 0.5, Pos.y(cell) + 0.5, Pos.z(cell) + 0.5,
					distance, false) != null;
		}

		/** Scans queued chunks section by section and reactivates every fluid cell found. */
		private void scanChunks(ServerLevel level, FluidConfig config) {
			int budget = Math.max(1, config.wakeChunksPerTick);
			while (budget-- > 0 && !queue.isEmpty()) {
				ChunkPos pos = queue.poll();
				if (!level.getChunkSource().hasChunk(pos.x(), pos.z())) {
					continue;
				}
				scanChunk(level.getChunk(pos.x(), pos.z()));
			}
		}

		private void scanChunk(net.minecraft.world.level.chunk.LevelChunk chunk) {
			net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
			int baseX = chunk.getPos().getMinBlockX();
			int baseZ = chunk.getPos().getMinBlockZ();
			int minY = chunk.getMinY();
			for (int index = 0; index < sections.length; index++) {
				net.minecraft.world.level.chunk.LevelChunkSection section = sections[index];
				if (section == null || section.hasOnlyAir()) {
					continue;
				}
				if (!section.maybeHas(FLUID_STATE)) {
					continue;
				}
				int sectionY = minY + (index << 4);
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							if (section.getBlockState(x, y, z).getBlock() instanceof RealisticFluidBlock) {
								activeCells.add(Pos.pack(baseX + x, sectionY + y, baseZ + z));
							}
						}
					}
				}
			}
		}

		private void applyReactions(ServerLevel level, FluidConfig config) {
			if (!config.waterLavaReaction && !config.lavaIgnitesFire) {
				return;
			}
			int budget = 256;
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			long[] cells = new long[Math.min(activeCells.size(), budget)];
			int count = activeCells.copyInto(cells, cells.length);
			for (int i = 0; i < count; i++) {
				cursor.set(Pos.x(cells[i]), Pos.y(cells[i]), Pos.z(cells[i]));
				BlockState state = level.getBlockState(cursor);
				if (!(state.getBlock() instanceof RealisticFluidBlock block)) {
					continue;
				}
				BlockPos pos = cursor.immutable();
				if (block.kind().isWater()) {
					FluidReactions.waterRandomTick(level, pos, state);
				} else {
					FluidReactions.lavaTick(level, pos, state);
				}
			}
		}

		private void activate(ServerLevel level, BlockPos pos) {
			activeCells.add(Pos.pack(pos.getX(), pos.getY(), pos.getZ()));
			for (var direction : net.minecraft.core.Direction.values()) {
				BlockPos neighbour = pos.relative(direction);
				if (level.isLoaded(neighbour)) {
					activeCells.add(Pos.pack(neighbour.getX(), neighbour.getY(), neighbour.getZ()));
				}
			}
		}

		private void wakeChunk(ChunkPos pos) {
			if (queue.size() > 8192 || queue.contains(pos)) {
				return;
			}
			queue.add(pos);
		}
	}

	/** Immutable counters for {@code /arfluids stats}. */
	public record StatsSnapshot(int activeCells, int parkedCells, int queuedChunks, int visited, int moved,
			long steps) {
	}
}
