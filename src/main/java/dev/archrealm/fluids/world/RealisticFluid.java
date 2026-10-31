package dev.archrealm.fluids.world;

import java.util.Optional;
import java.util.function.Supplier;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.registry.ModBlocks;
import dev.archrealm.fluids.sim.FluidKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * The {@link Fluid} implementation behind ArchRealm Fluids.
 *
 * <p>Unlike vanilla there is no separate "still" and "flowing" fluid: a single fluid per kind holds
 * {@code 1..8} units of volume in its state, and the level property maps that volume directly. The
 * fluid never ticks and never spreads on its own — {@link FluidEngine} moves it — so vanilla's flow
 * logic can never fight the simulation.
 */
public final class RealisticFluid extends FlowingFluid {
	/** Amount stored in a completely full cell. */
	public static final int FULL = 8;

	private final FluidKind kind;
	private final Supplier<Item> bucket;

	public RealisticFluid(FluidKind kind, Supplier<Item> bucket) {
		this.kind = kind;
		this.bucket = bucket;
	}

	public FluidKind kind() {
		return kind;
	}

	// --- state / volume ---------------------------------------------------

	@Override
	protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
		super.createFluidStateDefinition(builder);
		builder.add(LEVEL);
	}

	@Override
	public int getAmount(FluidState state) {
		return Math.max(1, Math.min(FULL, state.getValue(LEVEL)));
	}

	@Override
	public boolean isSource(FluidState state) {
		return getAmount(state) >= FULL;
	}

	@Override
	public Fluid getFlowing() {
		return this;
	}

	@Override
	public Fluid getSource() {
		return this;
	}

	@Override
	public FluidState getSource(boolean falling) {
		return getFlowing(FULL, falling);
	}

	/** Creates the fluid state that represents {@code units} (1..8) of this fluid. */
	public FluidState stateOf(int units) {
		int clamped = Math.max(1, Math.min(FULL, units));
		return this.defaultFluidState().setValue(LEVEL, clamped);
	}

	// --- vanilla plumbing -------------------------------------------------

	@Override
	public Item getBucket() {
		return bucket.get();
	}

	@Override
	protected BlockState createLegacyBlock(FluidState state) {
		BlockState blockState = kind.isWater()
				? ModBlocks.WATER.defaultBlockState()
				: ModBlocks.LAVA.defaultBlockState();
		return blockState.setValue(LiquidBlock.LEVEL, legacyLevel(getAmount(state)));
	}

	/** Maps a volume (1..8) to the block level property (0 = full, 7 = a single unit). */
	public static int legacyLevel(int units) {
		return Math.max(0, Math.min(7, FULL - units));
	}

	@Override
	public int getTickDelay(LevelReader level) {
		// Never scheduled by us; a large value keeps stray vanilla scheduling harmless.
		return 100;
	}

	@Override
	protected float getExplosionResistance() {
		return 100.0F;
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other,
										Direction direction) {
		return false;
	}

	@Override
	protected int getDropOff(LevelReader level) {
		return 1;
	}

	@Override
	protected int getSlopeFindDistance(LevelReader level) {
		return 1;
	}

	@Override
	protected boolean canConvertToSource(ServerLevel level) {
		return false;
	}

	@Override
	public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {
		// The simulation owns all movement. Vanilla ticking is deliberately disabled so that the
		// volume model can never drift out of sync with the world.
	}

	@Override
	protected void animateTick(Level level, BlockPos pos, FluidState fluidState, RandomSource random) {
		if (!ArchRealmFluids.config().reactionParticles) {
			return;
		}
		if (!fluidState.isSource() && random.nextInt(48) == 0) {
			double x = pos.getX() + random.nextDouble();
			double y = pos.getY() + fluidState.getOwnHeight();
			double z = pos.getZ() + random.nextDouble();
			level.addParticle(kind.isLava() ? ParticleTypes.LAVA : ParticleTypes.UNDERWATER,
					x, y, z, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		Block.dropResources(state, level, pos, blockEntity);
	}

	@Override
	protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier) {
		if (kind.isWater()) {
			effectApplier.apply(InsideBlockEffectType.EXTINGUISH);
		} else {
			effectApplier.apply(InsideBlockEffectType.CLEAR_FREEZE);
			effectApplier.apply(InsideBlockEffectType.LAVA_IGNITE);
		}
	}

	@Override
	public Optional<SoundEvent> getPickupSound() {
		return Optional.of(kind.isLava() ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL);
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == this;
	}

	@Override
	public String toString() {
		return "RealisticFluid[" + kind + "]";
	}
}
