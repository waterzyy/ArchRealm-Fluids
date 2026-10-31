package dev.archrealm.fluids.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A drain: while it is switched on it removes one unit of realistic fluid per interval from the block
 * below, no matter which kind of fluid it is. Fluids of the same body flow towards it, so it empties a
 * pool from the bottom like a real drain.
 */
public class FluidDrainBlock extends Block {
	public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
	private static final int TICK_INTERVAL = 10;

	public FluidDrainBlock(Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(ENABLED, Boolean.FALSE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ENABLED);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(ENABLED)) {
			return;
		}
		FluidEngine.drain(level, pos.below(), null, 1);
		level.scheduleTick(pos, this, TICK_INTERVAL);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (state.getValue(ENABLED)) {
			level.scheduleTick(pos, this, TICK_INTERVAL);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean enabled = !state.getValue(ENABLED);
		level.setBlock(pos, state.setValue(ENABLED, enabled), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, enabled ? 1.2F : 0.8F);
		if (player != null) {
			player.sendSystemMessage(Component.translatable(enabled
					? "block.archrealm_fluids.drain.on"
					: "block.archrealm_fluids.drain.off"));
		}
		if (enabled) {
			level.scheduleTick(pos, this, TICK_INTERVAL);
		}
		return InteractionResult.CONSUME;
	}
}
