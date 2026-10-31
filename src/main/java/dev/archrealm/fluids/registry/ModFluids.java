package dev.archrealm.fluids.registry;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.sim.FluidKind;
import dev.archrealm.fluids.world.RealisticFluid;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

/**
 * Fluid registry entries. One {@link Fluid} per kind; the volume lives in the fluid state's level
 * property, so there is no separate "still" and "flowing" fluid.
 */
public final class ModFluids {
	public static final RealisticFluid WATER = new RealisticFluid(FluidKind.WATER, ModItems::waterBucket);
	public static final RealisticFluid LAVA = new RealisticFluid(FluidKind.LAVA, ModItems::lavaBucket);

	private ModFluids() {
	}

	public static void register() {
		register("realistic_water", WATER);
		register("realistic_lava", LAVA);
	}

	private static void register(String name, Fluid fluid) {
		Registry.register(BuiltInRegistries.FLUID, Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, name), fluid);
	}

	/** Looks up the fluid instance for a simulation kind. */
	public static RealisticFluid of(FluidKind kind) {
		return kind.isWater() ? WATER : LAVA;
	}
}
