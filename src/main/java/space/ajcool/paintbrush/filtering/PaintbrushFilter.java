package space.ajcool.paintbrush.filtering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.ajcool.paintbrush.tokenizer.TokenDeserializer;
import space.ajcool.paintbrush.tokenizer.TokenLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

/**
 * The PaintbrushFilter class is responsible for loading and managing the filter item identifiers used in the Paintbrush mod.
 * It reads the filter data from a JSON resource file and initializes a list of filter item identifiers that can be used for filtering paintable blocks.
 */
public class PaintbrushFilter {

    public static final Logger LOGGER = LoggerFactory.getLogger("PaintbrushFilter");

    private static List<String> FILTER_NAMES = List.of();

    /**
     * Loads the filter item identifiers from the paintbrush-filter.json resource file.
     * The JSON file is expected to contain a list of strings, each representing a filter item identifier.
     * If the file is not found or an error occurs during loading, an empty filter list will be initialized.
     */
    @SuppressWarnings("unchecked")
    public static void load(){

        Identifier id = new Identifier("paintbrush", "paintbrush-filter.json");

        Optional<Resource> resource = MinecraftClient.getInstance()
                .getResourceManager()
                .getResource(id);

        if (resource.isPresent()) {

            try (InputStream stream = resource.get().getInputStream()) {

                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(TokenLoader.TokenData.class, new TokenDeserializer())
                        .create();

                List<String> filters = gson.fromJson(new InputStreamReader(stream), List.class);

                FILTER_NAMES = filters.stream().map(String::toLowerCase).toList();

                LOGGER.info("Paintbrush - Initialized {} filter items", FILTER_NAMES.size());

            } catch (IOException e) {
                LOGGER.warn("Paintbrush - Could not find paintbrush-filter.json!");
                LOGGER.info("Initializing empty filter list");
                LOGGER.error("Error during filter data initialization", e);
            }
        }
    }

    /**
     * Checks if the given BlockState matches any of the filter item identifiers.
     * The method converts the BlockState to a string and checks if it contains any of the filter names (case-insensitive).
     *
     * @param targetBlockState The BlockState to be checked against the filter item identifiers.
     * @return true if the BlockState matches any filter item identifier, false otherwise.
     */
    public static boolean contains(BlockState targetBlockState) {

        if (targetBlockState == null) return false;

        var blockade = targetBlockState.toString().toLowerCase();

        for (String filterName : FILTER_NAMES)
            if (blockade.contains(filterName)) return true;

        return false;
    }
}
