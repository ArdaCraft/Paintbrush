package space.ajcool.paintbrush;

import com.conquestrefabricated.core.item.family.FamilyRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

/**
 * Builds display names for paintbrush item stacks from their stored paintbrush data component.
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
     * @param itemStack           the paintbrush stack whose {@code paintbrush} custom-data compound contains material, state, and size data
     * @param registryEntryLookup the block registry wrapper used to decode a stored strict-mode block state
     * @return the custom name to apply to the paintbrush stack
     */
    public static MutableComponent buildBrushName(ItemStack itemStack, HolderGetter<Block> registryEntryLookup)
    {
        var paintNbt = PaintbrushData.read(itemStack);
        var iHaveAState = false;
        BlockState blockState;

        if (paintNbt.contains("state"))
        {
            var state = paintNbt.getCompound("state").orElseGet(net.minecraft.nbt.CompoundTag::new);
            blockState = NbtUtils.readBlockState(registryEntryLookup, state);
            iHaveAState = true;
        }
        else
        {
            var material = paintNbt.getStringOr("material", "minecraft:air");
            var paintIdentifier = Identifier.parse(material);
            var paintFamily = FamilyRegistry.BLOCKS.getFamily(paintIdentifier);
            blockState = paintFamily.isAbsent()
                    ? BuiltInRegistries.BLOCK.getValue(paintIdentifier).defaultBlockState()
                    : paintFamily.getRoot().defaultBlockState();
        }

        var localName = Component.translatable(blockState.getBlock().getDescriptionId());
        var name = Component.translatable("paintbrush.item.name", localName)
                .withStyle(iHaveAState ? ChatFormatting.RED : ChatFormatting.AQUA);

        if (paintNbt.contains("size"))
        {
            var size = paintNbt.getIntOr("size", 1);
            if (size > 1) name.append(Component.translatable("paintbrush.item.size_suffix", size).withStyle(ChatFormatting.GRAY));
        }

        return name;
    }

    public static MutableComponent prefixedMessage(Component body)
    {
        return Component.empty()
                .append(Component.translatable("paintbrush.prefix").withStyle(ChatFormatting.DARK_AQUA))
                .append(body);
    }
}
