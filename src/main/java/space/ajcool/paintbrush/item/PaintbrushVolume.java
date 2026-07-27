package space.ajcool.paintbrush.item;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import space.ajcool.paintbrush.config.PaintbrushConfig;
import space.ajcool.paintbrush.filtering.PaintbrushFilter;

import java.util.ArrayList;
import java.util.List;

public final class PaintbrushVolume
{
    private PaintbrushVolume()
    {
    }

    public static List<BlockPos> collect(World world, BlockPos center, int size)
    {
        var positions = new ArrayList<BlockPos>();

        if (size <= 1)
        {
            positions.add(center);
            return positions;
        }

        var radius = size - 1;

        for (int x = -radius; x <= radius; x++)
        {
            for (int y = -radius; y <= radius; y++)
            {
                for (int z = -radius; z <= radius; z++)
                {
                    if (x * x + y * y + z * z <= radius * radius)
                    {
                        positions.add(center.add(x, y, z));
                    }
                }
            }
        }

        return positions;
    }

    public static boolean isPaintable(World world, BlockPos pos)
    {
        var targetBlockState = world.getBlockState(pos);
        var isFluid = targetBlockState.getFluidState() != null && !targetBlockState.getFluidState().isEmpty();

        if (!world.canSetBlock(pos) || targetBlockState.isAir() || isFluid) return false;

        return !PaintbrushConfig.FILTER_FOLIAGE || !PaintbrushFilter.contains(targetBlockState);
    }
}
