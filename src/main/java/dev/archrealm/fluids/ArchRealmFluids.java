package dev.archrealm.fluids;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.archrealm.fluids.command.FluidCommands;
import dev.archrealm.fluids.config.FluidConfig;
import dev.archrealm.fluids.config.FluidConfigManager;
import dev.archrealm.fluids.registry.ModBlockEntities;
import dev.archrealm.fluids.registry.ModCreativeTab;
import dev.archrealm.fluids.registry.ModBlocks;
import dev.archrealm.fluids.registry.ModFluids;
import dev.archrealm.fluids.registry.ModItems;
import dev.archrealm.fluids.world.FluidEngine;

/**
 * Entry point of ArchRealm Fluids: realistic, server-authoritative water and lava.
 *
 * <p>Water and lava are finite volumes instead of endless vanilla sources. A deterministic solver moves
 * them inside a hard per-tick budget, the world stores them as ordinary block states, and vanilla
 * renders them - which is what keeps Sodium, Iris and shader packs working.
 */
public final class ArchRealmFluids implements ModInitializer {
	public static final String MOD_ID = "archrealm_fluids";
	public static final Logger LOGGER = LoggerFactory.getLogger("ArchRealmFluids");
	private static FluidConfig config = new FluidConfig();

	@Override
	public void onInitialize() {
		FluidConfigManager.load();
		config = FluidConfigManager.get();
		config.validate();
		for (String fix : config.validateFixes) {
			LOGGER.warn("Config: {}", fix);
		}

		ModFluids.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModCreativeTab.register();
		FluidEngine.register();
		FluidCommands.register();

		LOGGER.info("ArchRealm Fluids ready: {} cell updates/step, {} steps/s, radius {}",
				config.cellUpdatesPerStep, config.stepsPerSecond, config.simulationRadius);
	}

	/** The live configuration; never null. */
	public static FluidConfig config() {
		return config;
	}

	/** Mod version from the metadata, or {@code unknown} if it cannot be read. */
	public static String version() {
		return net.fabricmc.loader.api.FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	/** Replaces the configuration, e.g. after {@code /arfluids reload}. */
	public static void reloadConfig() {
		FluidConfigManager.load();
		config = FluidConfigManager.get();
		config.validate();
	}
}
