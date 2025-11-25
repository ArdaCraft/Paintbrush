package space.ajcool.paintbrush.tokenizer;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenDeserializer implements JsonDeserializer<TokenLoader.TokenData> {

    private static final Pattern OPTIONAL_PATTERN = Pattern.compile("(.*?)\\(([^)]+)\\)(.*)");

    @Override
    public TokenLoader.TokenData deserialize(JsonElement json, Type typeOfT,
                                             JsonDeserializationContext context) throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();

        TokenLoader.TokenData data = new TokenLoader.TokenData();
        data.reserved_names = expandAndSort(obj.getAsJsonArray("reserved_names"));
        data.tokens = expandAndSort(obj.getAsJsonArray("tokens"));

        return data;
    }

    private List<String> expandAndSort(JsonArray arr) {
        List<String> result = new ArrayList<>();

        for (JsonElement el : arr) {
            String s = el.getAsString().toLowerCase();

            Matcher m = OPTIONAL_PATTERN.matcher(s);

            if (m.matches()) {
                String before = m.group(1);
                String inner = m.group(2);
                String after  = m.group(3);

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

