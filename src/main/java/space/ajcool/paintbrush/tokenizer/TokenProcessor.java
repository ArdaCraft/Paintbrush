package space.ajcool.paintbrush.tokenizer;

import net.minecraft.block.Block;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import space.ajcool.paintbrush.Paintbrush;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class TokenProcessor {

    public boolean tokenizedBlocksMatch(Pair<Block, List<String>> blockA, Pair<Block, List<String>> blockB){

        return new HashSet<>(blockA.getRight()).equals(
                new HashSet<>(blockB.getRight()));
    }

    public Pair<Block, List<String>> tokenizeBlock(Block block){

        String blockFullName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();

        for (String reservedName : TokenRegistry.RESERVED_TOKENS) {

            if (blockFullName.contains(reservedName)) {

                blockFullName = blockFullName.replaceAll(reservedName, "");
                break;
            }
        }

        List<String> blockTokens = Arrays.stream(blockFullName.split(" "))
                .filter(TokenRegistry.TOKENS::contains)
                .toList();

        return new Pair<>(block, blockTokens);
    }

    public void outputDebug(Pair<Block, List<String>> tokenizedTargetBlock, List<Pair<Block, List<String>>> tokenizedPaintFamilyBlocks) {

        var blockName =  Text.translatable(tokenizedTargetBlock.getLeft().getTranslationKey()).getString();

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
