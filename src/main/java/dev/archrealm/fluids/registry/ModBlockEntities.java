package dev.archrealm.fluids.registry;

import dev.archrealm.fluids.ArchRealmFluids;
import dev.archrealm.fluids.world.WobbleBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block entity registry entries. */
public final class ModBlockEntities {
	public static final BlockEntityType<WobbleBlockEntity> WOBBLE = new BlockEntityType<>(
			WobbleBlockEntity::new, Set.of(ModBlocks.WOBBLE_SLIME, ModBlocks.WOBBLE_HONEY));

	private ModBlockEntities() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
				Identifier.fromNamespaceAndPath(ArchRealmFluids.MOD_ID, "wobble_block"), WOBBLE);
	}
}
