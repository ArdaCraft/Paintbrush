package space.ajcool.paintbrush.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.ajcool.paintbrush.Paintbrush;

/**
 * Mixin for PlayerEntity to suppress block activation when holding paintbrush or paint knife.
 * Prevents accidental block interaction (e.g., opening doors, activating switches) while painting.
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    /**
     * Suppresses block interactions (doors, buttons, levers, etc.) when the player is holding
     * the paintbrush or paint knife and block toggle suppression is enabled.
     *
     * @param cir callback to allow cancelling the default behaviour
     */
    @Inject(method = "shouldCancelInteraction", at = @At("HEAD"), cancellable = true)
    private void paintbrush$suppressBlockToggles(CallbackInfoReturnable<Boolean> cir) {
        var player = (PlayerEntity) (Object) this;
        if (!Paintbrush.isBlockTogglesDisabled(player.getUuid())) return;

        var item = player.getMainHandStack().getItem();
        if (item == Paintbrush.PAINTBRUSH_ITEM || item == Paintbrush.PAINT_KNIFE_ITEM) {
            cir.setReturnValue(true);
        }
    }
}
