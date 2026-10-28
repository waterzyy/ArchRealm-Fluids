package dev.archrealm.fluids.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The "realistic" slime and honey blocks: jelly cubes that wobble and deform when something lands on
 * them and that keep vanilla's bouncy (slime) or sticky (honey) movement.
 *
 * <p>The deformation itself is drawn client side from {@link WobbleBlockEntity}; the block only turns
 * physics events into impulses, so a server without the client still behaves correctly.
 */
public class WobbleBlock extends Block implements EntityBlock {
	public enum Kind {
		SLIME,
		HONEY
	}

	private final Kind kind;

	public WobbleBlock(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WobbleBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return (tickLevel, pos, tickState, blockEntity) -> {
			if (blockEntity instanceof WobbleBlockEntity wobble) {
				WobbleBlockEntity.tick(tickLevel, pos, tickState, wobble);
			}
		};
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		if (!entity.isSuppressingBounce()) {
			entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
		}
		if (kind == Kind.SLIME && fallDistance > 0.4 && !entity.isSuppressingBounce()) {
			// Vanilla slime bounces the entity back up; keep that, it is what players expect.
			entity.setDeltaMovement(entity.getDeltaMovement().x * 0.8,
					Math.min(1.0, fallDistance * 0.18), entity.getDeltaMovement().z * 0.8);
		}
		impulse(level, pos, (float) Math.min(0.9, 0.25 + fallDistance * 0.16));
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
		if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
			impulse(level, pos, 0.12F);
		}
		if (kind == Kind.SLIME) {
			double vertical = Math.abs(entity.getDeltaMovement().y);
			if (vertical < 0.1 && !entity.isSteppingCarefully()) {
				double scale = 0.4 + vertical * 0.2;
				entity.setDeltaMovement(entity.getDeltaMovement().multiply(scale, 1.0, scale));
			}
		} else {
			entity.makeStuckInBlock(onState, new Vec3(0.5, 0.65, 0.5));
			entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.4, 1.0, 0.4));
		}
		super.stepOn(level, pos, onState, entity);
	}

	private static void impulse(Level level, BlockPos pos, float amount) {
		if (level.getBlockEntity(pos) instanceof WobbleBlockEntity wobble) {
			wobble.impulse(amount);
		}
	}
}
