package space.ajcool.paintbrush.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtInt;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.PaintbrushNaming;
import space.ajcool.paintbrush.compat.PaintbrushCompat;

/**
 * Intercepts mouse wheel input before vanilla hotbar scrolling so Ctrl+scroll can resize the held paintbrush.
 * The priority is intentionally higher than Axiom's default mixin priority so this narrowly scoped handler runs first.
 */
@Environment(EnvType.CLIENT)
@Mixin(value = Mouse.class, priority = 1500)
public class MouseMixin
{
    /**
     * Handles Ctrl+vertical scroll while a paintbrush is held in the main hand.
     * All other scroll input is allowed to continue to vanilla and other mods.
     *
     * @param window     the GLFW window handle that received the scroll event
     * @param horizontal the horizontal scroll amount
     * @param vertical   the vertical scroll amount used to increment or decrement brush size
     * @param ci         callback state used to cancel vanilla handling after Paintbrush consumes the event
     */
    @Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void paintbrush$ctrlScrollBrushSize(long window, double horizontal, double vertical, CallbackInfo ci)
    {
        if (vertical == 0) return;
        if (!Screen.hasControlDown()) return;

        var client = MinecraftClient.getInstance();
        if (client.currentScreen != null) return;

        var player = client.player;
        if (player == null || player.isSpectator()) return;
        if (PaintbrushCompat.axiomEditorActive()) return;

        var itemStack = player.getInventory().getMainHandStack();
        if (!itemStack.isOf(Paintbrush.PAINTBRUSH_ITEM)) return;

        var paintNbt = itemStack.getOrCreateSubNbt("paintbrush");
        var currentSize = paintNbt.contains("size") ? paintNbt.getInt("size") : 1;
        int size = (int) (currentSize + Math.signum(vertical));
        size = size < 1 ? 1 : Math.min(size, 5);

        paintNbt.put("size", NbtInt.of(size));

        RegistryWrapper<Block> registryEntryLookup = player.getWorld() != null
                ? player.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK)
                : Registries.BLOCK.getReadOnlyWrapper();
        itemStack.setCustomName(PaintbrushNaming.buildBrushName(itemStack, registryEntryLookup));

        var packetBuffer = PacketByteBufs.create();
        packetBuffer.writeInt(player.getInventory().getSlotWithStack(itemStack));
        packetBuffer.writeItemStack(itemStack);

        ClientPlayNetworking.send(Paintbrush.SET_ITEMSTACK_PACKET_ID, packetBuffer);

        client.inGameHud.heldItemTooltipFade = 40;

        ci.cancel();
    }
}
