package space.ajcool.paintbrush.tokenizer;

import java.util.List;

/**
 * Registry for tokens used in block name matching.
 * Tokens are words from block IDs that are compared between blocks to find family variants.
 * Reserved tokens are material names and ambiguous phrases stripped before tokenization.
 */
public class TokenRegistry {

    /** List of reserved token names that should be stripped before tokenizing a block name. */
    public static List<String> RESERVED_TOKENS = List.of();

    /** List of meaningful tokens that are compared between blocks for matching. */
    public static List<String> TOKENS = List.of();

    /**
     * Updates the token and reserved token registries.
     * Called during resource reload with data from tokens.json.
     *
     * @param reserved the list of reserved token names
     * @param tokens   the list of meaningful tokens
     */
    public static void setTokens(List<String> reserved, List<String> tokens) {

        RESERVED_TOKENS = List.copyOf(reserved);
        TOKENS = List.copyOf(tokens);
    }
}