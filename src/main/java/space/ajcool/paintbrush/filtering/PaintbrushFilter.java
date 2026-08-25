package space.ajcool.paintbrush.filtering;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages foliage filtering for the paintbrush.
 * Loads block tags and name patterns from paintbrush-filter.json to identify foliage-like blocks.
 * Filtered blocks are invisible to brush targeting and are not painted when filtering is enabled.
 */
@Environment(EnvType.CLIENT)
public final class PaintbrushFilter {

    /** Logger for filter operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger("PaintbrushFilter");

    /** List of lowercase block state substrings to match against foliage. */
    private static List<String> FILTER_NAMES = List.of();

    /** Set of block identifiers that are matched exactly. */
    private static Set<Identifier> FILTER_IDS = Set.of();

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
     * Entries are #namespace:id block tag references, namespace:id block identifiers matched
     * exactly, or bare keywords matched as lowercase block state substrings.
     */
    public static void load() {
        var id = new Identifier("paintbrush", "paintbrush-filter.json");
        Optional<Resource> resource = MinecraftClient.getInstance()
                .getResourceManager()
                .getResource(id);

        if (resource.isEmpty()) {
            clear();
            LOGGER.warn("Paintbrush - Could not find paintbrush-filter.json!");
            return;
        }

        try (InputStream stream = resource.get().getInputStream()) {
            var type = new TypeToken<List<String>>() {
            }.getType();
            List<String> data = new Gson().fromJson(new InputStreamReader(stream), type);

            if (data == null) {
                clear();
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
            FILTER_IDS = entries.stream()
                    .filter(name -> !name.startsWith("#") && name.contains(":"))
                    .map(PaintbrushFilter::parseIdentifier)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet());
            FILTER_NAMES = entries.stream()
                    .filter(name -> !name.startsWith("#") && !name.contains(":"))
                    .toList();
            LOGGER.info("Paintbrush - Initialized {} filter blocks, {} filter keywords and {} filter tags",
                    FILTER_IDS.size(), FILTER_NAMES.size(), FILTER_TAGS.size());
        } catch (Exception e) {
            clear();
            LOGGER.warn("Paintbrush - Could not load paintbrush-filter.json!");
            LOGGER.error("Error during paintbrush filter initialization", e);
        }
    }

    /**
     * Drops every loaded filter entry.
     */
    private static void clear() {
        FILTER_NAMES = List.of();
        FILTER_IDS = Set.of();
        FILTER_TAGS = List.of();
    }

    /**
     * Parses a block identifier from a filter entry string.
     *
     * @param name the namespace:id entry string
     * @return an optional containing the parsed identifier, or empty if parsing failed
     */
    private static Optional<Identifier> parseIdentifier(String name) {
        var identifier = Identifier.tryParse(name);

        if (identifier == null) {
            LOGGER.warn("Paintbrush - Skipping malformed block filter entry '{}'", name);
            return Optional.empty();
        }

        return Optional.of(identifier);
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
     * Returns true if the block matches any filter tag, has a filtered block identifier, or
     * contains any filter keyword substring.
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

        if (!FILTER_IDS.isEmpty() && FILTER_IDS.contains(Registries.BLOCK.getId(blockState.getBlock()))) {
            return true;
        }

        if (FILTER_NAMES.isEmpty()) {
            return false;
        }

        var blockStateString = blockState.toString().toLowerCase(Locale.ROOT);
        return FILTER_NAMES.stream().anyMatch(blockStateString::contains);
    }
}
