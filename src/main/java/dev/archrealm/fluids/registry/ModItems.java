package dev.archrealm.fluids.registry;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.item.RealisticBucketItem;
import dev.archrealm.fluids.sim.FluidKind;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Item registry entries. */
public final class ModItems {
	public static final RealisticBucketItem WATER_BUCKET = new RealisticBucketItem(FluidKind.WATER,
			new Item.Properties().stacksTo(1).setId(key("realistic_water_bucket")));
	public static final RealisticBucketItem LAVA_BUCKET = new RealisticBucketItem(FluidKind.LAVA,
			new Item.Properties().stacksTo(1).setId(key("realistic_lava_bucket")));

	public static final BlockItem OUTLET = new BlockItem(ModBlocks.OUTLET,
			new Item.Properties().setId(key("realistic_source")));
	public static final BlockItem LAVA_OUTLET = new BlockItem(ModBlocks.LAVA_OUTLET,
			new Item.Properties().setId(key("realistic_lava_source")));
	public static final BlockItem DRAIN = new BlockItem(ModBlocks.DRAIN,
			new Item.Properties().setId(key("realistic_drain")));
	public static final BlockItem SLIME = new BlockItem(ModBlocks.WOBBLE_SLIME,
			new Item.Properties().setId(key("realistic_slime_block")));
	public static final BlockItem HONEY = new BlockItem(ModBlocks.WOBBLE_HONEY,
			new Item.Properties().setId(key("realistic_honey_block")));

	private ModItems() {
	}

	public static void register() {
		register("realistic_water_bucket", WATER_BUCKET);
		register("realistic_lava_bucket", LAVA_BUCKET);
		register("realistic_source", OUTLET);
		register("realistic_lava_source", LAVA_OUTLET);
		register("realistic_drain", DRAIN);
		register("realistic_slime_block", SLIME);
		register("realistic_honey_block", HONEY);
	}

	private static void register(String name, Item item) {
		Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, name), item);
	}

	private static ResourceKey<Item> key(String name) {
		return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, name));
	}

	public static Item waterBucket() {
		return WATER_BUCKET;
	}

	public static Item lavaBucket() {
		return LAVA_BUCKET;
	}

	public static ItemStack waterBucketStack() {
		return new ItemStack(WATER_BUCKET);
	}

	public static ItemStack lavaBucketStack() {
		return new ItemStack(LAVA_BUCKET);
	}
}
