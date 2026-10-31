package dev.archrealm.fluids.world;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.config.FluidConfig;
import dev.archrealm.fluids.registry.ModBlocks;
import dev.archrealm.fluids.sim.FluidKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Everything the realistic fluids do to the world around them: water and lava meeting, lava setting
 * flammables on fire, water putting fires out and water keeping farmland wet.
 *
 * <p>All of this is driven by the simulation, not by vanilla fluid ticks, so it only happens where
 * fluid is actually moving, and it is fully configurable.
 */
public final class FluidReactions {
	private static final Direction[] SIDES = {
			Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
	};

	private FluidReactions() {
	}

	/** Called for every moving water cell: hydrates farmland below, quenches neighbouring lava. */
	public static void waterRandomTick(ServerLevel level, BlockPos pos, BlockState state) {
		FluidConfig config = ArchRealmFluids.config();
		if (config.waterHydratesFarmland) {
			BlockPos below = pos.below();
			BlockState belowState = level.getBlockState(below);
			if (belowState.is(Blocks.FARMLAND)) {
				int moisture = belowState.getValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE);
				if (moisture < 7) {
					level.setBlock(below, belowState.setValue(
							net.minecraft.world.level.block.FarmlandBlock.MOISTURE, moisture + 1), Block.UPDATE_CLIENTS);
				}
			}
		}
		if (config.waterLavaReaction) {
			for (Direction side : SIDES) {
				BlockPos neighbour = pos.relative(side);
				BlockState neighbourState = level.getBlockState(neighbour);
				if (neighbourState.getBlock() instanceof RealisticFluidBlock block && block.kind().isLava()) {
					quench(level, neighbour, neighbourState);
					break;
				}
			}
		}
		if (config.waterExtinguishesFire) {
			for (Direction side : SIDES) {
				BlockPos neighbour = pos.relative(side);
				if (level.getBlockState(neighbour).getBlock() instanceof BaseFireBlock) {
					level.setBlock(neighbour, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					level.playSound(null, neighbour, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
					break;
				}
			}
		}
	}

	/** Called for every moving lava cell: ignites flammables, cools when it touches water. */
	public static void lavaTick(ServerLevel level, BlockPos pos, BlockState state) {
		FluidConfig config = ArchRealmFluids.config();
		if (config.waterLavaReaction) {
			for (Direction side : SIDES) {
				BlockPos neighbour = pos.relative(side);
				BlockState neighbourState = level.getBlockState(neighbour);
				if (neighbourState.getBlock() instanceof RealisticFluidBlock block && block.kind().isWater()) {
					quench(level, pos, state);
					return;
				}
			}
		}
		if (config.lavaIgnitesFire && level.getRandom().nextInt(24) == 0) {
			BlockPos above = pos.above();
			if (level.getBlockState(above).isAir() && level.getBlockState(pos).isSolid()) {
				// Only light a fire when something flammable is close, like vanilla lava does.
				for (Direction side : SIDES) {
					if (level.getBlockState(pos.relative(side)).ignitedByLava()) {
						level.setBlock(above, BaseFireBlock.getState(level, above), Block.UPDATE_ALL);
						break;
					}
				}
			}
		}
	}

	/** Water meets lava: the lava turns to stone (or obsidian) and the water is consumed. */
	private static void quench(ServerLevel level, BlockPos lavaPos, BlockState lavaState) {
		boolean full = RealisticFluidBlock.unitsOf(lavaState) >= RealisticFluid.FULL;
		level.setBlock(lavaPos, (full ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, lavaPos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
				2.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8F);
		level.sendParticles(ParticleTypes.CLOUD, lavaPos.getX() + 0.5, lavaPos.getY() + 1.0,
				lavaPos.getZ() + 0.5, 8, 0.25, 0.2, 0.25, 0.02);
		// Cool the water that caused it, so a single unit can not turn an ocean into stone for free.
		for (Direction side : SIDES) {
			BlockPos neighbour = lavaPos.relative(side);
			BlockState neighbourState = level.getBlockState(neighbour);
			if (neighbourState.getBlock() instanceof RealisticFluidBlock block && block.kind().isWater()) {
				int left = RealisticFluidBlock.unitsOf(neighbourState) - 1;
				level.setBlock(neighbour, left <= 0 ? Blocks.AIR.defaultBlockState()
						: RealisticFluidBlock.stateFor(FluidKind.WATER, left), Block.UPDATE_CLIENTS);
				FluidEngine.wake(level, neighbour);
				break;
			}
		}
		FluidEngine.wake(level, lavaPos);
	}

}
