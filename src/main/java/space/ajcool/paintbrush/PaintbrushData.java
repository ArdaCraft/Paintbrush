package space.ajcool.paintbrush;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * Accesses the paintbrush payload stored inside the vanilla custom-data component.
 */
public final class PaintbrushData
{
    private static final String ROOT_KEY = "paintbrush";

    private PaintbrushData()
    {
    }

    public static CompoundTag read(ItemStack stack)
    {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getCompound(ROOT_KEY)
                .map(CompoundTag::copy)
                .orElseGet(CompoundTag::new);
    }

    public static void mutate(ItemStack stack, Consumer<CompoundTag> mutator)
    {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root ->
        {
            var paint = root.getCompound(ROOT_KEY)
                    .map(CompoundTag::copy)
                    .orElseGet(CompoundTag::new);
            mutator.accept(paint);
            root.put(ROOT_KEY, paint);
        });
    }
}
