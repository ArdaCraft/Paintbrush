package space.ajcool.paintbrush.tokenizer;

import net.minecraft.block.Block;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import space.ajcool.paintbrush.Paintbrush;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Processes block names into tokens for similarity matching.
 * Strips reserved names (material/shape ambiguities) and extracts meaningful tokens
 * to compare blocks when the exact family match is unavailable.
 */
public class TokenProcessor {

    /**
     * Checks if two tokenized blocks have matching token sets.
     * Blocks match if their token sets are equal (regardless of order).
     *
     * @param blockA the first tokenized block (block and its tokens)
     * @param blockB the second tokenized block (block and its tokens)
     * @return true if the blocks have the same token set
     */
    public boolean tokenizedBlocksMatch(Pair<Block, List<String>> blockA, Pair<Block, List<String>> blockB) {

        return new HashSet<>(blockA.getRight()).equals(
                new HashSet<>(blockB.getRight()));
    }

    /**
     * Tokenizes a block name by extracting meaningful tokens.
     * Removes reserved names first, then splits by spaces and filters to known tokens.
     *
     * @param block the block to tokenize
     * @return a pair of the block and its extracted tokens
     */
    public Pair<Block, List<String>> tokenizeBlock(Block block) {

        String blockFullName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();

        for (String reservedName : TokenRegistry.RESERVED_TOKENS) {

            if (blockFullName.contains(reservedName)) {

                blockFullName = blockFullName.replace(reservedName, "");
                break;
            }
        }

        List<String> blockTokens = Arrays.stream(blockFullName.split(" "))
                .filter(TokenRegistry.TOKENS::contains)
                .toList();

        return new Pair<>(block, blockTokens);
    }

    /**
     * Outputs debug information about tokenization to the log.
     * Shows the target block's tokens and all family candidates' tokens.
     *
     * @param tokenizedTargetBlock       the tokenized target block
     * @param tokenizedPaintFamilyBlocks all tokenized candidates from the paint family
     */
    public void outputDebug(Pair<Block, List<String>> tokenizedTargetBlock, List<Pair<Block, List<String>>> tokenizedPaintFamilyBlocks) {

        var blockName = Text.translatable(tokenizedTargetBlock.getLeft().getTranslationKey()).getString();

        StringBuilder builder = new StringBuilder("Looking for match of \"")
                .append(blockName)
                .append("\" [")
                .append(tokenizedTargetBlock.getRight().toString())
                .append("] in paint family : ");
        Paintbrush.LOGGER.info(builder.toString());

        builder = new StringBuilder();

        for (Pair<Block, List<String>> pair : tokenizedPaintFamilyBlocks) {

            builder.append("- \"")
                    .append(Text.translatable(pair.getLeft().getTranslationKey()).getString())
                    .append("\" [")
                    .append(pair.getRight().toString())
                    .append("]\n");
        }
        Paintbrush.LOGGER.info(builder.toString());
    }
}
