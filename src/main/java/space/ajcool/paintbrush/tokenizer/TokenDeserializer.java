package space.ajcool.paintbrush.tokenizer;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom JSON deserializer for token data.
 * Handles tokens with optional pluralization syntax (e.g., "board(s)" becomes "board" and "boards").
 * Sorts tokens longest-first for consistent matching.
 */
public class TokenDeserializer implements JsonDeserializer<TokenLoader.TokenData> {

    /** Pattern for optional token syntax: (before)(inner)(after). */
    private static final Pattern OPTIONAL_PATTERN = Pattern.compile("(.*?)\\(([^)]+)\\)(.*)");

    /**
     * Deserializes JSON token data.
     * Expands optional pluralization patterns and sorts results by length.
     *
     * @param json    the JSON element to deserialize
     * @param typeOfT the type of the object to deserialize
     * @param context the deserialization context
     * @return the deserialized token data
     * @throws JsonParseException if the JSON is invalid
     */
    @Override
    public TokenLoader.TokenData deserialize(JsonElement json, Type typeOfT,
                                             JsonDeserializationContext context) throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();

        TokenLoader.TokenData data = new TokenLoader.TokenData();
        data.reserved_names = expandAndSort(obj.getAsJsonArray("reserved_names"));
        data.tokens = expandAndSort(obj.getAsJsonArray("tokens"));

        return data;
    }

    /**
     * Expands optional pluralization patterns and sorts the result.
     * For example: "board(s)" becomes ["boards", "board"] (longest first).
     *
     * @param arr the JSON array of tokens to process
     * @return a list of expanded and sorted tokens
     */
    private List<String> expandAndSort(JsonArray arr) {
        List<String> result = new ArrayList<>();

        for (JsonElement el : arr) {
            String s = el.getAsString().toLowerCase();

            Matcher m = OPTIONAL_PATTERN.matcher(s);

            if (m.matches()) {
                String before = m.group(1);
                String inner = m.group(2);
                String after = m.group(3);

                // singular (remove parentheses)
                String singular = (before + after).trim();

                // plural (insert content normally)
                String plural = (before + inner + after).trim();

                result.add(singular);
                result.add(plural);

            } else {
                result.add(s);
            }
        }

        // Sort longest → shortest
        result.sort((a, b) -> Integer.compare(b.length(), a.length()));

        return result;
    }
}

