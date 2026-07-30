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
import space.ajcool.paintbrush.compat.PaintbrushCompat;
import space.ajcool.paintbrush.config.PaintbrushConfig;
import space.ajcool.paintbrush.filtering.FilteredRaycast;
import space.ajcool.paintbrush.filtering.PaintbrushFilter;
import space.ajcool.paintbrush.render.PaintbrushHighlightState;

/**
 * Mixin for GameRenderer to update raycasting behaviour with foliage filtering.
 * When foliage filtering is enabled and the paintbrush/paint knife is held,
 * makes foliage blocks transparent to targeting.
 * Priority 1500 ensures this runs after Axiom's RETURN injector at default priority 1000.
 */
@Mixin(value = GameRenderer.class, priority = 1500)
public class GameRendererMixin {

    /** The Minecraft client instance. */
    @Shadow
    @Final
    MinecraftClient client;

    /**
     * Injects into updateTargetedEntity to apply foliage filtering to crosshair targeting.
     * Replaces the crosshair target with a filtered raycast when the initial target is foliage,
     * or when a MISS occurs at extended Axiom reach distance.
     * Runs at TAIL with priority 1500 to execute after Axiom's injections.
     *
     * @param tickDelta the time since the last tick
     * @param ci        the callback info for this injection
     */
    @Inject(method = "updateTargetedEntity", at = @At("TAIL"))
    private void updateFilteredTarget(float tickDelta, CallbackInfo ci) {
        PaintbrushHighlightState.OCCLUDED = false;

        if (!PaintbrushConfig.FILTER_FOLIAGE) return;
        if (client.player == null || client.player.isSpectator()) return;
        if (client.interactionManager == null) return;

        var itemStack = client.player.getMainHandStack();
        if (!itemStack.isOf(Paintbrush.PAINTBRUSH_ITEM) && !itemStack.isOf(Paintbrush.PAINT_KNIFE_ITEM)) return;

        var vanillaReach = client.interactionManager.getReachDistance();
        var reach = PaintbrushCompat.targetingReach(vanillaReach);

        if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
            if (blockHitResult.getType() != HitResult.Type.BLOCK) return;

            var blockState = client.player.getWorld().getBlockState(blockHitResult.getBlockPos());
            if (!PaintbrushFilter.contains(blockState)) return;

            client.crosshairTarget = FilteredRaycast.raycast(client.player, reach);
        } else if (reach > vanillaReach && client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.MISS) {
            // Extended reach with a miss: re-raycast to find foliage or blocks beyond vanilla range
            client.crosshairTarget = FilteredRaycast.raycast(client.player, reach);
        } else {
            return;
        }

        if (client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            PaintbrushHighlightState.OCCLUDED = true;
        }
    }
}
