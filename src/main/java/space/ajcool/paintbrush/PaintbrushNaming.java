package space.ajcool.paintbrush;

import com.conquestrefabricated.core.item.family.FamilyRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Builds display names for paintbrush item stacks from their stored paintbrush NBT.
 * This class is common-code safe so both server commands and client interactions can use the same naming rules.
 */
public final class PaintbrushNaming
{
    /**
     * Prevents instantiation of this utility class.
     */
    private PaintbrushNaming()
    {
    }

    /**
     * Builds the custom paintbrush name for the supplied item stack.
     * Strict-mode brushes are colored red, material-family brushes are colored aqua, and sizes above one are appended.
     *
     * @param itemStack           the paintbrush stack whose {@code paintbrush} sub-NBT contains material, state, and size data
     * @param registryEntryLookup the block registry wrapper used to decode a stored strict-mode block state
     * @return the custom name to apply to the paintbrush stack
     */
    public static MutableText buildBrushName(ItemStack itemStack, RegistryWrapper<Block> registryEntryLookup)
    {
        var paintNbt = itemStack.getOrCreateSubNbt("paintbrush");
        var iHaveAState = false;
        BlockState blockState;

        if (paintNbt.contains("state"))
        {
            var state = paintNbt.getCompound("state");
            blockState = NbtHelper.toBlockState(registryEntryLookup, state);
            iHaveAState = true;
        }
        else
        {
            var material = paintNbt.getString("material");
            var paintIdentifier = new Identifier(material);
            var paintFamily = FamilyRegistry.BLOCKS.getFamily(paintIdentifier);
            blockState = paintFamily.isAbsent()
                    ? Registries.BLOCK.get(paintIdentifier).getDefaultState()
                    : paintFamily.getRoot().getDefaultState();
        }

        var localName = Text.translatable(blockState.getBlock().getTranslationKey());
        var name = Text.empty()
                .append(localName)
                .append(" Paintbrush")
                .formatted(iHaveAState ? Formatting.RED : Formatting.AQUA);

        if (paintNbt.contains("size"))
        {
            var size = paintNbt.getInt("size");
            if (size > 1) name.append(Text.literal(" (" + size + ")").formatted(Formatting.GRAY));
        }

        return name;
    }
}
