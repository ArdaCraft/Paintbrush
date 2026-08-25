package space.ajcool.paintbrush.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.PaintbrushData;
import space.ajcool.paintbrush.PaintbrushNaming;
import space.ajcool.paintbrush.compat.PaintbrushCompat;

/**
 * Intercepts mouse wheel input before vanilla hotbar scrolling so Ctrl+scroll can resize the held paintbrush.
 * The priority is intentionally higher than Axiom's default mixin priority so this narrowly scoped handler runs first.
 */
@Environment(EnvType.CLIENT)
@Mixin(value = MouseHandler.class, priority = 1500)
public class MouseMixin
{
    /**
     * Handles Ctrl+vertical scroll while a paintbrush is held in the main hand.
     * All other scroll input is allowed to continue to vanilla and other mods.
     *
     * @param handle     the GLFW window handle that received the scroll event
     * @param xoffset the horizontal scroll amount
     * @param yoffset   the vertical scroll amount used to increment or decrement brush size
     * @param ci         callback state used to cancel vanilla handling after Paintbrush consumes the event
     */
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void paintbrush$ctrlScrollBrushSize(long handle, double xoffset, double yoffset, CallbackInfo ci)
    {
        if (yoffset == 0) return;
        if (!isControlDown()) return;

        var client = Minecraft.getInstance();
        if (client.screen != null) return;

        var player = client.player;
        if (player == null || player.isSpectator()) return;
        if (PaintbrushCompat.axiomEditorActive()) return;

        var itemStack = player.getInventory().getSelectedItem();
        if (!itemStack.is(Paintbrush.PAINTBRUSH_ITEM)) return;

        var paintNbt = PaintbrushData.read(itemStack);
        var currentSize = paintNbt.getIntOr("size", 1);
        int size = (int) (currentSize + Math.signum(yoffset));
        size = size < 1 ? 1 : Math.min(size, 5);

        var newSize = size;
        PaintbrushData.mutate(itemStack, data -> data.putInt("size", newSize));

        HolderGetter<Block> registryEntryLookup = player.level().registryAccess().lookupOrThrow(Registries.BLOCK);
        itemStack.set(DataComponents.CUSTOM_NAME, PaintbrushNaming.buildBrushName(itemStack, registryEntryLookup));

        ClientPlayNetworking.send(new Paintbrush.SetItemStackPayload(itemStack));

        client.gui.toolHighlightTimer = 40;

        ci.cancel();
    }

    @Unique
    private static boolean isControlDown()
    {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL);
    }
}
