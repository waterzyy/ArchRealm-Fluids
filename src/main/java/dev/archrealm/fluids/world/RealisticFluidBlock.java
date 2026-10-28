package dev.archrealm.fluids.world;

import dev.archrealm.fluids.registry.ModBlocks;
import dev.archrealm.fluids.sim.FluidKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * A block that holds a finite amount of realistic fluid.
 *
 * <p>The block is a normal {@link LiquidBlock} so that vanilla keeps doing what it is good at —
 * collision, light, bucket pickup, entity effects — but every piece of vanilla flow logic is replaced:
 * the block schedules no fluid ticks, never asks the fluid to spread and instead only tells
 * {@link FluidEngine} that the neighbourhood changed.
 *
 * <p>The amount is stored in the vanilla {@code level} property (0 = full, 7 = one unit), which means
 * the volume is part of the chunk data and therefore saved, loaded and sent to clients for free.
 */
public class RealisticFluidBlock extends LiquidBlock {
	public static final int MAX_UNITS = RealisticFluid.FULL;

	private final FluidKind kind;

	public RealisticFluidBlock(FluidKind kind, FlowingFluid fluid, Properties properties) {
		super(fluid, properties);
		this.kind = kind;
	}

	public FluidKind kind() {
		return kind;
	}

	/** Amount of fluid stored in this block state, in units (0 if empty). */
	public int units(BlockState state) {
		return MAX_UNITS - state.getValue(LEVEL);
	}

	public static int unitsOf(BlockState state) {
		return state.getBlock() instanceof RealisticFluidBlock block ? block.units(state) : 0;
	}

	public static int unitsIn(BlockState state, FluidKind kind) {
		return state.getBlock() instanceof RealisticFluidBlock block && block.kind == kind ? block.units(state) : 0;
	}

	/** Builds the block state for {@code units} (1..8) of the given fluid. */
	public static BlockState stateFor(FluidKind kind, int units) {
		Block block = kind.isWater() ? ModBlocks.WATER : ModBlocks.LAVA;
		int clamped = Math.max(1, Math.min(MAX_UNITS, units));
		return block.defaultBlockState().setValue(LEVEL, MAX_UNITS - clamped);
	}

	// --- vanilla hooks ----------------------------------------------------

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return kind.isWater();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return kind.isWater();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		// Never defer to vanilla flow; the simulation owns all movement.
		FluidEngine.wake(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
			@Nullable Orientation orientation, boolean movedByPiston) {
		FluidEngine.wake(level, pos);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (level instanceof Level realLevel) {
			FluidEngine.wake(realLevel, pos);
		}
		return state;
	}

	/**
	 * A bucket can only be filled from a cell that holds a whole block of fluid — exactly like
	 * vanilla. Partial cells are emptied with the drain block instead, which keeps the strict volume
	 * bookkeeping (no unit is ever created or lost by a bucket).
	 */
	@Override
	public ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
		if (unitsOf(state) < MAX_UNITS) {
			return ItemStack.EMPTY;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		if (level instanceof Level realLevel) {
			FluidEngine.wake(realLevel, pos);
		}
		return new ItemStack(this.fluid.getBucket());
	}

	@Override
	protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
			net.minecraft.world.phys.shapes.CollisionContext context) {
		return net.minecraft.world.phys.shapes.Shapes.empty();
	}
}
