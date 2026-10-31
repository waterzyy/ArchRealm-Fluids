package dev.archrealm.fluids.world;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.sim.FluidKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * An outlet that pours realistic fluid into the block below it while it is switched on.
 *
 * <p>It emits one unit per interval rather than a whole bucket, so it works like a tap: a running tap
 * fills a pool gradually and stops the moment it is switched off or the source is broken.
 */
public class FluidOutletBlock extends Block {
	public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
	private static final int TICK_INTERVAL = 10;

	private final FluidKind kind;

	public FluidOutletBlock(FluidKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(this.stateDefinition.any().setValue(ENABLED, Boolean.FALSE));
	}

	public FluidKind kind() {
		return kind;
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
		int placed = FluidEngine.pour(level, pos.below(), kind, 1);
		if (placed > 0 && level.getRandom().nextInt(4) == 0) {
			level.sendParticles(kind.isLava() ? ParticleTypes.LAVA : ParticleTypes.SPLASH,
					pos.getX() + 0.5, pos.getY() - 0.2, pos.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.0);
		}
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
					? "block.archrealm_fluids.outlet.on"
					: "block.archrealm_fluids.outlet.off"));
		}
		if (enabled) {
			level.scheduleTick(pos, this, TICK_INTERVAL);
			ArchRealmFluids.LOGGER.debug("Outlet opened at {}", pos);
		}
		return InteractionResult.CONSUME;
	}
}
