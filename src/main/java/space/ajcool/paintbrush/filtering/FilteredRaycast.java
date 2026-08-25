package space.ajcool.paintbrush.filtering;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Performs raycasting with foliage filtering applied.
 * Skips over filtered blocks (foliage) and air to target the next solid block.
 */
@Environment(EnvType.CLIENT)
public final class FilteredRaycast {

    /**
     * Private constructor to prevent instantiation.
     */
    private FilteredRaycast() {
    }

    /**
     * Performs a filtered raycast from the player's eye position.
     * Ignores air blocks and filtered foliage blocks during the raycast.
     *
     * @param player the player performing the raycast
     * @param reach  the maximum distance to raycast
     * @return the hit result, either hitting a block or missing
     */
    public static BlockHitResult raycast(Player player, double reach) {
        var start = player.getEyePosition();
        var end = start.add(player.getViewVector(1.0F).scale(reach));
        var context = new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        );

        return BlockGetter.traverseBlocks(start, end, context, (raycastContext, pos) ->
        {
            var world = player.level();
            var state = world.getBlockState(pos);
            if (state.isAir() || PaintbrushFilter.contains(state)) return null;

            return world.clipWithInteractionOverride(
                    raycastContext.getFrom(),
                    raycastContext.getTo(),
                    pos,
                    raycastContext.getBlockShape(state, world, pos),
                    state
            );
        }, _ ->
        {
            var direction = end.subtract(start);
            return BlockHitResult.miss(
                    end,
                    Direction.getApproximateNearest(direction),
                    BlockPos.containing(end)
            );
        });
    }
}
