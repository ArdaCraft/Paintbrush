package space.ajcool.paintbrush.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
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
 * Mixin for Minecraft to apply foliage filtering to crosshair targeting.
 * When foliage filtering is enabled and the paintbrush/paint knife is held,
 * makes foliage blocks transparent to targeting by replacing the crosshair
 * target with a filtered raycast that skips over foliage.
 * <p>
 * This injects at the TAIL of {@code Minecraft.pick(float)} — the method that
 * actually computes {@code minecraft.hitResult} each frame — so the replacement
 * is the final target used by both the block outline and the interaction.
 * (In earlier versions this logic lived in {@code GameRenderer.update}, but as of
 * 26.1 that method only reads {@code hitResult}; the pick moved to {@code Minecraft}.)
 * Priority 1500 keeps it running after Axiom's injections.
 */
@Mixin(value = Minecraft.class, priority = 1500)
public class MinecraftMixin {

    /**
     * Replaces the crosshair target with a filtered raycast when the initial target is foliage,
     * or when a MISS occurs at extended Axiom reach distance. Runs at TAIL of the pick that sets
     * {@code hitResult}, after Axiom's injections.
     *
     * @param partialTicks the frame partial-tick value
     * @param ci           the callback info for this injection
     */
    @Inject(method = "pick", at = @At("TAIL"))
    private void paintbrush$filterPickTarget(float partialTicks, CallbackInfo ci) {
        var minecraft = (Minecraft) (Object) this;

        PaintbrushHighlightState.OCCLUDED = false;

        if (!PaintbrushConfig.FILTER_FOLIAGE) return;
        if (minecraft.player == null || minecraft.player.isSpectator()) return;
        if (minecraft.gameMode == null) return;

        var itemStack = minecraft.player.getMainHandItem();
        if (!itemStack.is(Paintbrush.PAINTBRUSH_ITEM) && !itemStack.is(Paintbrush.PAINT_KNIFE_ITEM)) return;

        var vanillaReach = minecraft.player.blockInteractionRange();
        var reach = PaintbrushCompat.targetingReach(vanillaReach);

        if (minecraft.hitResult instanceof BlockHitResult blockHitResult) {
            if (blockHitResult.getType() != HitResult.Type.BLOCK) return;

            var blockState = minecraft.player.level().getBlockState(blockHitResult.getBlockPos());
            if (!PaintbrushFilter.contains(blockState)) return;

            minecraft.hitResult = FilteredRaycast.raycast(minecraft.player, reach);
        } else if (reach > vanillaReach && minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.MISS) {
            // Extended reach with a miss: re-raycast to find foliage or blocks beyond vanilla range
            minecraft.hitResult = FilteredRaycast.raycast(minecraft.player, reach);
        } else {
            return;
        }

        if (minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            PaintbrushHighlightState.OCCLUDED = true;
        }
    }
}
