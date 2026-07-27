package space.ajcool.paintbrush.item;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import space.ajcool.paintbrush.config.PaintbrushConfig;
import space.ajcool.paintbrush.filtering.PaintbrushFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing the volume of blocks affected by the paintbrush.
 * Collects positions within a spherical radius and checks paintability constraints.
 */
public final class PaintbrushVolume {
    /**
     * Private constructor to prevent instantiation.
     */
    @SuppressWarnings("unused")
    private PaintbrushVolume() {
    }

    /**
     * Collects all block positions within a spherical volume centered at the given position.
     * Uses radius = size - 1, where size 1 is a single block, size 2 is a 3x3x3 sphere, etc.
     *
     * @param ignoredWorld the world (unused, but provides context)
     * @param center       the centre position of the sphere
     * @param size         the brush size (1-5)
     * @return a list of all positions within the sphere
     */
    public static List<BlockPos> collect(World ignoredWorld, BlockPos center, int size) {
        var positions = new ArrayList<BlockPos>();

        if (size <= 1) {
            positions.add(center);
            return positions;
        }

        var radius = size - 1;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        positions.add(center.add(x, y, z));
                    }
                }
            }
        }

        return positions;
    }

    /**
     * Determines whether a block at the given position can be painted.
     * Returns false for air, fluids, protected blocks, and (when filtering is on) foliage blocks.
     *
     * @param world the world containing the block
     * @param pos   the position to check
     * @return true if the block can be painted
     */
    public static boolean isPaintable(World world, BlockPos pos) {
        var targetBlockState = world.getBlockState(pos);
        var isFluid = targetBlockState.getFluidState() != null && !targetBlockState.getFluidState().isEmpty();

        if (!world.canSetBlock(pos) || targetBlockState.isAir() || isFluid) return false;

        return !PaintbrushConfig.FILTER_FOLIAGE || !PaintbrushFilter.contains(targetBlockState);
    }
}
