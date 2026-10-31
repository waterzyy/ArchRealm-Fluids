package dev.archrealm.fluids.item;

import dev.archrealm.fluids.sim.FluidKind;
import dev.archrealm.fluids.world.FluidEngine;
import dev.archrealm.fluids.world.RealisticFluid;
import dev.archrealm.fluids.world.RealisticFluidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bucket that pours realistic fluid into the world.
 *
 * <p>Pouring consumes the bucket and hands back an empty vanilla bucket, exactly like vanilla. The
 * reverse direction is free: vanilla buckets pick realistic fluid up through
 * {@code LiquidBlock#pickupBlock}, so a full cell can always be collected with an empty bucket.
 */
public class RealisticBucketItem extends Item {
	private final FluidKind kind;

	public RealisticBucketItem(FluidKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public FluidKind kind() {
		return kind;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		BlockState clicked = level.getBlockState(clickedPos);

		if (clicked.getBlock() instanceof RealisticFluidBlock) {
			// Clicking fluid never pours: that is what the empty bucket is for.
			return InteractionResult.PASS;
		}

		BlockPos target = clicked.canBeReplaced() ? clickedPos : clickedPos.relative(context.getClickedFace());
		BlockState targetState = level.getBlockState(target);
		boolean replaceable = targetState.canBeReplaced()
				|| (targetState.getBlock() instanceof RealisticFluidBlock other && other.kind() == kind);
		if (!replaceable) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}
		int placed = FluidEngine.pour(serverLevel, target, kind, RealisticFluid.FULL);
		if (placed <= 0) {
			return InteractionResult.PASS;
		}
		level.playSound(null, target, kind.isLava() ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY,
				SoundSource.BLOCKS, 1.0F, 1.0F);
		giveBackEmptyBucket(context, serverLevel);
		return InteractionResult.SUCCESS;
	}

	private void giveBackEmptyBucket(UseOnContext context, ServerLevel level) {
		Player player = context.getPlayer();
		if (player == null) {
			return;
		}
		ItemStack stack = context.getItemInHand();
		if (player.getAbilities().instabuild) {
			return;
		}
		stack.shrink(1);
		ItemStack empty = new ItemStack(Items.BUCKET);
		if (stack.isEmpty()) {
			player.setItemInHand(context.getHand() == null ? InteractionHand.MAIN_HAND : context.getHand(), empty);
		} else if (!player.getInventory().add(empty)) {
			Block.popResource(level, player.blockPosition(), empty);
		}
	}
}
