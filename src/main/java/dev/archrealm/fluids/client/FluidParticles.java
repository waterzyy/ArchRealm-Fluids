package dev.archrealm.fluids.client;

import dev.archrealm.fluids.sim.FluidKind;
import dev.archrealm.fluids.world.RealisticFluid;
import dev.archrealm.fluids.world.RealisticFluidBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;

/**
 * The "particle" water view.
 *
 * <p>In this mode the fluid surface is not drawn at all; instead a handful of droplets drift above
 * every visible fluid block, which makes the volume and its motion easy to read. The scan is
 * budgeted per tick and walks a moving slice of the area around the player, so the cost stays flat
 * even in an ocean.
 */
public final class FluidParticles {
	private static final int MAX_PARTICLES_PER_TICK = 48;
	private static final int RADIUS = 16;

	private static int cursor;
	private static int cachedCells;

	private FluidParticles() {
	}

	/** Number of fluid cells seen by the last {@link #tick(Minecraft)}. */
	public static int count() {
		return cachedCells;
	}

	/** Advances the particle view by one client tick. */
	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			cachedCells = 0;
			return;
		}
		boolean particles = FluidView.mode() == FluidView.Mode.PARTICLES;
		int budget = particles ? MAX_PARTICLES_PER_TICK : 0;
		BlockPos center = client.player.blockPosition();
		int y = center.getY() + (cursor % 9) - 4;
		cursor++;
		int cells = 0;
		int spawned = 0;
		for (int dx = -RADIUS; dx <= RADIUS; dx += 2) {
			for (int dz = -RADIUS; dz <= RADIUS; dz += 2) {
				BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
				if (!level.hasChunkAt(pos)) {
					continue;
				}
				var state = level.getBlockState(pos);
				if (!(state.getBlock() instanceof RealisticFluidBlock fluid)) {
					continue;
				}
				cells++;
				if (spawned >= budget) {
					continue;
				}
				int units = RealisticFluidBlock.unitsOf(state);
				if (units <= 0) {
					continue;
				}
				float height = Mth.clamp(units / (float) RealisticFluid.FULL, 0.05F, 1.0F);
				ParticleOptions particle = fluid.kind() == FluidKind.LAVA
						? ParticleTypes.LAVA : ParticleTypes.FALLING_WATER;
				level.addParticle(particle,
						pos.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.9,
						pos.getY() + height * 0.95,
						pos.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.9,
						0.0, 0.03, 0.0);
				spawned++;
			}
		}
		cachedCells = cells;
	}
}
