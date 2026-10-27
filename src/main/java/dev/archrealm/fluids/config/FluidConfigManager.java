package dev.archrealm.fluids.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import dev.archrealm.fluids.ArchRealmFluids;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads, validates and saves {@link FluidConfig}.
 *
 * <p>Loading never throws: a broken file is renamed to {@code .broken} and defaults are used, so a
 * typo can never stop a server from starting.
 */
public final class FluidConfigManager {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.setLenient()
			.create();
	private static volatile FluidConfig config = new FluidConfig();

	private FluidConfigManager() {
	}

	public static FluidConfig get() {
		return config;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("archrealm_fluids.json");
	}

	/** Reads the config file, creating it with defaults when it does not exist yet. */
	public static void load() {
		Path path = path();
		FluidConfig loaded = new FluidConfig();
		if (Files.exists(path)) {
			try {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				FluidConfig parsed = GSON.fromJson(json, FluidConfig.class);
				if (parsed != null) {
					loaded = parsed;
				}
			} catch (JsonSyntaxException | IOException e) {
				ArchRealmFluids.LOGGER.error("Could not read {} - falling back to defaults", path, e);
				backup(path);
			}
		}
		loaded.validate();
		config = loaded;
		if (!loaded.validateFixes.isEmpty()) {
			for (String fix : List.copyOf(loaded.validateFixes)) {
				ArchRealmFluids.LOGGER.warn("Config: {}", fix);
			}
		}
		save();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ArchRealmFluids.LOGGER.error("Could not save {}", path, e);
		}
	}

	/** Replaces the live configuration (used by {@code /arfluids reload}). */
	public static void set(FluidConfig newConfig) {
		newConfig.validate();
		config = newConfig;
		save();
	}

	private static void backup(Path path) {
		try {
			Path broken = path.resolveSibling(path.getFileName() + ".broken");
			Files.move(path, broken, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// best effort only
		}
	}
}
