package space.ajcool.paintbrush.filtering;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

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
    public static BlockHitResult raycast(PlayerEntity player, double reach) {
        var start = player.getEyePos();
        var end = start.add(player.getRotationVec(1.0F).multiply(reach));
        var context = new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        );

        return BlockView.raycast(start, end, context, (raycastContext, pos) ->
        {
            var world = player.getWorld();
            var state = world.getBlockState(pos);
            if (state.isAir() || PaintbrushFilter.contains(state)) return null;

            return world.raycastBlock(
                    raycastContext.getStart(),
                    raycastContext.getEnd(),
                    pos,
                    raycastContext.getBlockShape(state, world, pos),
                    state
            );
        }, raycastContext ->
        {
            var direction = end.subtract(start);
            return BlockHitResult.createMissed(
                    end,
                    Direction.getFacing(direction.x, direction.y, direction.z),
                    BlockPos.ofFloored(end)
            );
        });
    }
}
