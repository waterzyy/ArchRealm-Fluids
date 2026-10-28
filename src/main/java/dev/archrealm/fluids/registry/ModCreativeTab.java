package dev.archrealm.fluids.registry;

import dev.archrealm.fluids.ArchRealmFluids;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * A dedicated creative tab, so every item of the mod sits together instead of being scattered through
 * the vanilla categories.
 */
public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, "fluids"));

	private ModCreativeTab() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, CreativeModeTab
				.builder(CreativeModeTab.Row.TOP, 7)
				.title(Component.translatable("itemGroup.archrealm_fluids"))
				.icon(() -> new ItemStack(ModItems.WATER_BUCKET))
				.displayItems((parameters, output) -> {
					output.accept(ModItems.WATER_BUCKET);
					output.accept(ModItems.LAVA_BUCKET);
					output.accept(ModItems.OUTLET);
					output.accept(ModItems.LAVA_OUTLET);
					output.accept(ModItems.DRAIN);
					output.accept(ModItems.SLIME);
					output.accept(ModItems.HONEY);
				})
				.build());
	}
}
