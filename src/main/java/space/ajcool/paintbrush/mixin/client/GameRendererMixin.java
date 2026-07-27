package space.ajcool.paintbrush.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.config.PaintbrushConfig;
import space.ajcool.paintbrush.filtering.FilteredRaycast;
import space.ajcool.paintbrush.filtering.PaintbrushFilter;
import space.ajcool.paintbrush.render.PaintbrushHighlightState;

@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "updateTargetedEntity", at = @At("TAIL"))
    private void updateFilteredTarget(float tickDelta, CallbackInfo ci)
    {
        PaintbrushHighlightState.OCCLUDED = false;

        if (!PaintbrushConfig.FILTER_FOLIAGE) return;
        if (client.player == null || client.player.isSpectator()) return;
        if (client.interactionManager == null) return;

        var itemStack = client.player.getMainHandStack();
        if (!itemStack.isOf(Paintbrush.PAINTBRUSH_ITEM) && !itemStack.isOf(Paintbrush.PAINT_KNIFE_ITEM)) return;
        if (!(client.crosshairTarget instanceof BlockHitResult blockHitResult)) return;
        if (blockHitResult.getType() != HitResult.Type.BLOCK) return;

        var blockState = client.player.getWorld().getBlockState(blockHitResult.getBlockPos());
        if (!PaintbrushFilter.contains(blockState)) return;

        client.crosshairTarget = FilteredRaycast.raycast(client.player, client.interactionManager.getReachDistance());

        if (client.crosshairTarget.getType() == HitResult.Type.BLOCK)
        {
            PaintbrushHighlightState.OCCLUDED = true;
        }
    }
}
