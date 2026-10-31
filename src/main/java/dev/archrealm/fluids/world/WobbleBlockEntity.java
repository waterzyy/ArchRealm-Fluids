package dev.archrealm.fluids.world;

import dev.archrealm.fluids.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stores the jelly wobble of a {@link WobbleBlock} so it can be drawn as a deformed cube.
 *
 * <p>The value decays on both sides. The server only sends a packet when the wobble is strong enough
 * to be visible, and a block at rest sends nothing at all, so a field of slime costs no bandwidth.
 */
public class WobbleBlockEntity extends BlockEntity {
	private static final float DECAY = 0.86F;
	private static final float MIN_VISIBLE = 0.01F;

	private float wobble;
	private float phase;

	public WobbleBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WOBBLE, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, WobbleBlockEntity blockEntity) {
		blockEntity.phase += 0.35F + blockEntity.wobble * 1.1F;
		if (blockEntity.wobble > 0.0F) {
			blockEntity.wobble *= DECAY;
			if (blockEntity.wobble < MIN_VISIBLE) {
				blockEntity.wobble = 0.0F;
			}
		}
	}

	/** Adds an impulse (0..1) to the wobble, typically from a landing or a step. */
	public void impulse(float amount) {
		float previous = wobble;
		wobble = Math.min(1.0F, wobble + Math.max(0.0F, amount));
		if (wobble <= previous) {
			return;
		}
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Current deformation strength, 0 when at rest. */
	public float wobble() {
		return wobble;
	}

	/** Animation clock in radians, advanced every tick. */
	public float phase() {
		return phase;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		wobble = input.getFloatOr("wobble", 0.0F);
		phase = input.getFloatOr("phase", 0.0F);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("wobble", wobble);
		output.putFloat("phase", phase);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("wobble", wobble);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
