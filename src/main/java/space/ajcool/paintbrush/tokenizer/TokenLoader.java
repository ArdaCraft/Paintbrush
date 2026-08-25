package space.ajcool.paintbrush.tokenizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

/**
 * Loads token data from the tokens.json resource file.
 * Tokens are used to match blocks across families when exact family matches aren't available.
 * Reserved names are material and shape names stripped before tokenization.
 */
@Environment(EnvType.CLIENT)
public final class TokenLoader {

    /** Logger for token loading operations. */
    public static final Logger LOGGER = LoggerFactory.getLogger("TokenLoader");

    /**
     * Loads token data from assets/paintbrush/tokens.json.
     * Parses JSON, expands optional pluralization patterns, and initializes the registry.
     * If loading fails, an empty token list is used as fallback.
     */
    public static void load() {

        Identifier id = new Identifier("paintbrush", "tokens.json");

        Optional<Resource> resource = MinecraftClient.getInstance()
                .getResourceManager()
                .getResource(id);

        if (resource.isPresent()) {

            try (InputStream stream = resource.get().getInputStream()) {

                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(TokenData.class, new TokenDeserializer())
                        .create();

                TokenData data = gson.fromJson(new InputStreamReader(stream), TokenData.class);

                // Sort reserved tokens by longest first
                data.reserved_names.sort((s1, s2) -> Integer.compare(s2.length(), s1.length()));

                TokenRegistry.setTokens(data.reserved_names, data.tokens);
                LOGGER.info("Paintbrush - Initialized {} tokens and {} reserved tokens", data.tokens.size(), data.reserved_names.size());

            } catch (IOException e) {
                LOGGER.warn("Paintbrush - Could not find tokens.json!");
                LOGGER.info("Initializing empty token list");
                LOGGER.error("Error during token data initialization", e);
            }
        }
    }

    /**
     * Container for deserialized token data from JSON.
     */
    public static class TokenData {

        /** Names of materials and shapes to strip before tokenization. */
        public List<String> reserved_names = List.of();

        /** Meaningful tokens extracted from block names. */
        public List<String> tokens = List.of();
    }
}