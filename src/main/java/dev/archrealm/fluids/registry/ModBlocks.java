package dev.archrealm.fluids.registry;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.sim.FluidKind;
import dev.archrealm.fluids.world.FluidDrainBlock;
import dev.archrealm.fluids.world.FluidOutletBlock;
import dev.archrealm.fluids.world.RealisticFluidBlock;
import dev.archrealm.fluids.world.WobbleBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Block registry entries. */
public final class ModBlocks {
	public static final RealisticFluidBlock WATER = new RealisticFluidBlock(FluidKind.WATER, ModFluids.WATER,
			fluidProperties("realistic_water", MapColor.WATER, 0));
	public static final RealisticFluidBlock LAVA = new RealisticFluidBlock(FluidKind.LAVA, ModFluids.LAVA,
			fluidProperties("realistic_lava", MapColor.FIRE, -1));
	public static final FluidOutletBlock OUTLET = new FluidOutletBlock(FluidKind.WATER,
			machineProperties("realistic_source", MapColor.COLOR_LIGHT_GRAY));
	public static final FluidOutletBlock LAVA_OUTLET = new FluidOutletBlock(FluidKind.LAVA,
			machineProperties("realistic_lava_source", MapColor.COLOR_ORANGE));
	public static final FluidDrainBlock DRAIN = new FluidDrainBlock(
			machineProperties("realistic_drain", MapColor.COLOR_GRAY));
	public static final WobbleBlock WOBBLE_SLIME = new WobbleBlock(WobbleBlock.Kind.SLIME,
			wobbleProperties("realistic_slime_block", MapColor.COLOR_LIGHT_GREEN, SoundType.SLIME_BLOCK));
	public static final WobbleBlock WOBBLE_HONEY = new WobbleBlock(WobbleBlock.Kind.HONEY,
			wobbleProperties("realistic_honey_block", MapColor.COLOR_ORANGE, SoundType.HONEY_BLOCK));

	private ModBlocks() {
	}

	public static void register() {
		register("realistic_water", WATER);
		register("realistic_lava", LAVA);
		register("realistic_source", OUTLET);
		register("realistic_lava_source", LAVA_OUTLET);
		register("realistic_drain", DRAIN);
		register("realistic_slime_block", WOBBLE_SLIME);
		register("realistic_honey_block", WOBBLE_HONEY);
	}

	private static void register(String name, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, name), block);
	}

	private static BlockBehaviour.Properties fluidProperties(String name, MapColor color, int light) {
		return BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, name)))
				.mapColor(color)
				.replaceable()
				.noCollision()
				.noOcclusion()
				.strength(100.0F)
				.pushReaction(PushReaction.POPPED)
				.noLootTable()
				.liquid()
				.sound(SoundType.EMPTY)
				.lightLevel(state -> light >= 0 ? light
						: Math.max(0, 15 - 2 * state.getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL)));
	}

	private static BlockBehaviour.Properties machineProperties(String name, MapColor color) {
		return BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, name)))
				.mapColor(color)
				.strength(2.0F, 6.0F)
				.sound(SoundType.METAL)
				.requiresCorrectToolForDrops();
	}

	private static BlockBehaviour.Properties wobbleProperties(String name, MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, name)))
				.mapColor(color)
				.strength(0.6F)
				.sound(sound)
				.noOcclusion()
				.friction(0.7F);
	}

	public static Block fluidBlock(FluidKind kind) {
		return kind.isWater() ? WATER : LAVA;
	}

	public static Block outlet(FluidKind kind) {
		return kind.isWater() ? OUTLET : LAVA_OUTLET;
	}
}
