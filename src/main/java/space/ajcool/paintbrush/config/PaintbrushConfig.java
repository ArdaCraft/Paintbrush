package space.ajcool.paintbrush.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import space.ajcool.paintbrush.Paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages client-side configuration for the Paintbrush mod.
 * Persists user preferences to config/paintbrush/paintbrush.json.
 * All fields are public static for easy access throughout the client code.
 */
@Environment(EnvType.CLIENT)
public class PaintbrushConfig {
    /** JSON serializer for configuration data. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Path to the configuration file in the config directory. */
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("paintbrush")
            .resolve("paintbrush.json");

    /** Whether the paint knife can delete blocks when decrementing layer to 0. */
    public static boolean PAINTKNIFE_ALLOW_DELETE = false;

    /** Whether the paint knife can append new layer blocks when incrementing past max. */
    public static boolean PAINTKNIFE_ALLOW_APPEND = false;

    /** How the paint knife promotes max-layer blocks to their family's full block. */
    public static FullBlockMode PAINTKNIFE_FULL_BLOCKS = FullBlockMode.PARTIAL;

    /** Whether paint knife debug messages should be shown client-side. */
    public static boolean PAINTKNIFE_DEBUG = false;

    /** Whether foliage-like blocks should be filtered (invisible to targeting and painting). */
    public static boolean FILTER_FOLIAGE = false;

    /** Whether block activation should be suppressed while holding the paintbrush or paint knife. */
    public static boolean DISABLE_BLOCK_TOGGLES = true;

    /**
     * Loads configuration from disk.
     * If the config file doesn't exist, defaults are used.
     * Any errors during loading will reset all settings to defaults.
     */
    public static void load() {
        if (!Files.exists(CONFIG_PATH)) return;

        try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
            var data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) return;

            PAINTKNIFE_ALLOW_DELETE = data.paintknifeAllowDelete;
            PAINTKNIFE_ALLOW_APPEND = data.paintknifeAllowAppend;
            PAINTKNIFE_FULL_BLOCKS = parseFullBlockMode(data.paintknifeFullBlocks, data.paintknifeAllowFullBlocks);
            PAINTKNIFE_DEBUG = data.paintknifeDebug;
            FILTER_FOLIAGE = data.filterFoliage;
            DISABLE_BLOCK_TOGGLES = data.disableBlockToggles;
        } catch (Exception e) {
            PAINTKNIFE_ALLOW_DELETE = false;
            PAINTKNIFE_ALLOW_APPEND = false;
            PAINTKNIFE_FULL_BLOCKS = FullBlockMode.PARTIAL;
            PAINTKNIFE_DEBUG = false;
            FILTER_FOLIAGE = false;
            DISABLE_BLOCK_TOGGLES = true;
            Paintbrush.LOGGER.warn("Paintbrush - Could not load config from {}", CONFIG_PATH, e);
        }
    }

    /**
     * Parses the full-block promotion mode from configuration data, with fallback to legacy format.
     * Attempts to parse the new modeName field first. If that fails, falls back to the legacy boolean.
     * If both are missing, defaults to PARTIAL.
     *
     * @param modeName    the new format mode name (enum value), or null/blank if not set
     * @param legacyValue the legacy boolean value (true = ALL, false = NONE), or null if not set
     * @return the parsed FullBlockMode
     */
    private static FullBlockMode parseFullBlockMode(String modeName, Boolean legacyValue) {
        if (modeName != null && !modeName.isBlank()) {
            try {
                return FullBlockMode.valueOf(modeName.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (legacyValue != null) {
            return legacyValue ? FullBlockMode.ALL : FullBlockMode.NONE;
        }

        return FullBlockMode.PARTIAL;
    }

    /**
     * Saves the current configuration to disk.
     * Creates the config directory if it doesn't exist.
     * Any errors during saving are logged as warnings.
     */
    public static void save() {
        var data = new ConfigData();
        data.paintknifeAllowDelete = PAINTKNIFE_ALLOW_DELETE;
        data.paintknifeAllowAppend = PAINTKNIFE_ALLOW_APPEND;
        data.paintknifeFullBlocks = PAINTKNIFE_FULL_BLOCKS.name();
        data.paintknifeDebug = PAINTKNIFE_DEBUG;
        data.filterFoliage = FILTER_FOLIAGE;
        data.disableBlockToggles = DISABLE_BLOCK_TOGGLES;

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            Paintbrush.LOGGER.warn("Paintbrush - Could not save config to {}", CONFIG_PATH, e);
        }
    }

    /**
     * Internal data class for JSON serialization of configuration settings.
     */
    private static class ConfigData {

        /** Whether paint knife block deletion is allowed. */
        boolean paintknifeAllowDelete = false;

        /** Whether paint knife block appending is allowed. */
        boolean paintknifeAllowAppend = false;

        /** Full-block promotion mode for maxed paint knife layer blocks. */
        String paintknifeFullBlocks = "PARTIAL";

        /** Legacy boolean for migrating older configs. */
        @SuppressWarnings("unused")
        Boolean paintknifeAllowFullBlocks;

        /** Whether paint knife debug output is enabled. */
        boolean paintknifeDebug = false;

        /** Whether foliage filtering is enabled. */
        boolean filterFoliage = false;

        /** Whether block activation is disabled for paintbrush and paint knife use. */
        boolean disableBlockToggles = true;
    }
}
