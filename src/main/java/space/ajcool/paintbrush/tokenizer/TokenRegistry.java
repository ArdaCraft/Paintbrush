package space.ajcool.paintbrush.tokenizer;

import java.util.List;

public class TokenRegistry {

    public static List<String> RESERVED_TOKENS = List.of();
    public static List<String> TOKENS = List.of();

    public static void setTokens(List<String> reserved, List<String> tokens) {

        RESERVED_TOKENS = List.copyOf(reserved);
        TOKENS = List.copyOf(tokens);
    }
}