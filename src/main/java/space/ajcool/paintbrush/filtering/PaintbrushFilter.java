package space.ajcool.paintbrush.filtering;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Manages foliage filtering for the paintbrush.
 * Loads block tags and name patterns from paintbrush-filter.json to identify foliage-like blocks.
 * Filtered blocks are invisible to brush targeting and are not painted when filtering is enabled.
 */
@Environment(EnvType.CLIENT)
public final class PaintbrushFilter {

    /** Logger for filter operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger("PaintbrushFilter");

    /** List of lowercase block state strings to match against foliage. */
    private static List<String> FILTER_NAMES = List.of();

    /** List of block tags that identify foliage blocks. */
    private static List<TagKey<Block>> FILTER_TAGS = List.of();

    /**
     * Private constructor to prevent instantiation.
     */
    @SuppressWarnings("unused")
    private PaintbrushFilter() {
    }

    /**
     * Loads the filter configuration from paintbrush-filter.json.
     * Entries can be lowercase block state substrings or #namespace:id block tag references.
     */
    public static void load() {
        var id = new Identifier("paintbrush", "paintbrush-filter.json");
        Optional<Resource> resource = MinecraftClient.getInstance()
                .getResourceManager()
                .getResource(id);

        if (resource.isEmpty()) {
            FILTER_NAMES = List.of();
            FILTER_TAGS = List.of();
            LOGGER.warn("Paintbrush - Could not find paintbrush-filter.json!");
            return;
        }

        try (InputStream stream = resource.get().getInputStream()) {
            var type = new TypeToken<List<String>>() {
            }.getType();
            List<String> data = new Gson().fromJson(new InputStreamReader(stream), type);

            if (data == null) {
                FILTER_NAMES = List.of();
                FILTER_TAGS = List.of();
                LOGGER.warn("Paintbrush - paintbrush-filter.json contains no filter items");
                return;
            }

            var entries = data.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .toList();

            FILTER_TAGS = entries.stream()
                    .filter(name -> name.startsWith("#"))
                    .map(PaintbrushFilter::parseTag)
                    .flatMap(Optional::stream)
                    .toList();
            FILTER_NAMES = entries.stream()
                    .filter(name -> !name.startsWith("#"))
                    .toList();
            LOGGER.info("Paintbrush - Initialized {} filter items and {} filter tags", FILTER_NAMES.size(), FILTER_TAGS.size());
        } catch (Exception e) {
            FILTER_NAMES = List.of();
            FILTER_TAGS = List.of();
            LOGGER.warn("Paintbrush - Could not load paintbrush-filter.json!");
            LOGGER.error("Error during paintbrush filter initialization", e);
        }
    }

    /**
     * Parses a tag identifier from a filter entry string.
     * Tag entries are prefixed with # (e.g., "#minecraft:leaves").
     *
     * @param name the tag entry string (including # prefix)
     * @return an optional containing the parsed tag, or empty if parsing failed
     */
    private static Optional<TagKey<Block>> parseTag(String name) {
        var identifier = Identifier.tryParse(name.substring(1));

        if (identifier == null) {
            LOGGER.warn("Paintbrush - Skipping malformed block tag filter entry '{}'", name);
            return Optional.empty();
        }

        return Optional.of(TagKey.of(RegistryKeys.BLOCK, identifier));
    }

    /**
     * Checks if a block state matches the foliage filter.
     * Returns true if the block matches any filter tag or contains any filter name substring.
     *
     * @param blockState the block state to check
     * @return true if the block is filtered (considered foliage)
     */
    public static boolean contains(BlockState blockState) {
        for (var tag : FILTER_TAGS) {
            if (blockState.isIn(tag)) {
                return true;
            }
        }

        if (FILTER_NAMES.isEmpty()) {
            return false;
        }

        var blockStateString = blockState.toString().toLowerCase(Locale.ROOT);
        return FILTER_NAMES.stream().anyMatch(blockStateString::contains);
    }
}
