package dev.archrealm.fluids.world;

import dev.archrealm.fluids.sim.FluidGrid;
import dev.archrealm.fluids.sim.FluidKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bridge between the pure simulation and a real {@link ServerLevel}.
 *
 * <p>The solver only ever sees plain integers: it asks how many units a cell holds, whether a cell can
 * be entered and how to write a value back. Keeping that behind this interface is what makes the solver
 * deterministic and unit testable without a running game.
 */
public final class LevelFluidGrid implements FluidGrid {
	private final ServerLevel level;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	public LevelFluidGrid(ServerLevel level) {
		this.level = level;
	}

	@Override
	public int fluidUnits(int x, int y, int z) {
		return RealisticFluidBlock.unitsOf(stateAt(x, y, z));
	}

	@Override
	public FluidKind fluidKind(int x, int y, int z) {
		BlockState state = stateAt(x, y, z);
		return state.getBlock() instanceof RealisticFluidBlock block ? block.kind() : FluidKind.WATER;
	}

	@Override
	public boolean isLoaded(int x, int y, int z) {
		if (y < level.getMinY() || y >= level.getMaxY()) {
			return false;
		}
		return level.getChunkSource().hasChunk(x >> 4, z >> 4);
	}

	@Override
	public void setFluid(int x, int y, int z, FluidKind kind, int units) {
		BlockPos pos = new BlockPos(x, y, z);
		BlockState current = level.getBlockState(pos);
		boolean ours = current.getBlock() instanceof RealisticFluidBlock block && block.kind() == kind;
		if (units <= 0) {
			if (ours) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			return;
		}
		if (!ours && !current.canBeReplaced()) {
			// Something solid was built here; never overwrite a player's block.
			return;
		}
		BlockState target = RealisticFluidBlock.stateFor(kind, units);
		if (target != current) {
			level.setBlock(pos, target, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public boolean canFlowInto(int x, int y, int z, FluidKind kind) {
		BlockState state = stateAt(x, y, z);
		if (state.getBlock() instanceof RealisticFluidBlock block) {
			return block.kind() == kind && RealisticFluidBlock.unitsOf(state) < RealisticFluid.FULL;
		}
		return state.canBeReplaced();
	}

	private BlockState stateAt(int x, int y, int z) {
		return level.getBlockState(cursor.set(x, y, z));
	}
}
