package space.ajcool.paintbrush.mixin.client;

import com.conquestrefabricated.core.item.family.FamilyRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtInt;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ajcool.paintbrush.Paintbrush;

/**
 * Mixin for PlayerInventory to handle Ctrl+scroll for changing brush size.
 * When holding the paintbrush and Ctrl is pressed, scrolling changes the brush size (1-5).
 * Updates the item name to reflect the new size and syncs with the server.
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {

    /**
     * Injects into scrollInHotbar to intercept scroll wheel events.
     * When Ctrl is held and paintbrush is selected, changes brush size instead of switching slots.
     *
     * @param scrollAmount the scroll wheel amount (positive = scroll up)
     * @param ci           the callback info for this injection
     */
    @Inject(
            method = "scrollInHotbar(D)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseScroll(double scrollAmount, CallbackInfo ci) {

        if (Screen.hasControlDown()) {
            var player = MinecraftClient.getInstance().player;
            if (player == null) return;

            var itemStack = player.getInventory().getMainHandStack();
            if (!itemStack.isOf(Paintbrush.PAINTBRUSH_ITEM)) return;

            var paintNbt = itemStack.getOrCreateSubNbt("paintbrush");

            int size = (int) (paintNbt.getInt("size") + Math.signum(scrollAmount));
            size = size < 1 ? 1 : (Math.min(size, 5));

            paintNbt.put("size", NbtInt.of(size));

            var iHaveAState = false;
            BlockState blockState;

            if (paintNbt.contains("state")) {
                var state = paintNbt.getCompound("state");
                RegistryWrapper<Block> registryEntryLookup = player.getWorld() != null ? player.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK) : Registries.BLOCK.getReadOnlyWrapper();
                blockState = NbtHelper.toBlockState(registryEntryLookup, state);
                iHaveAState = true;
            } else {
                var material = paintNbt.getString("material");
                var paintIdentifier = new Identifier(material);
                var paintFamily = FamilyRegistry.BLOCKS.getFamily(paintIdentifier);
                blockState = paintFamily.getRoot().getDefaultState();
            }

            var localName = Text.translatable(blockState.getBlock().getTranslationKey());
            var name = Text.empty()
                    .append(localName)
                    .append(" Paintbrush")
                    .formatted(iHaveAState ? Formatting.RED : Formatting.AQUA);

            if (size > 1) name.append(Text.literal(" (" + size + ")").formatted(Formatting.GRAY));

            itemStack.setCustomName(name);

            var packetBuffer = PacketByteBufs.create();
            packetBuffer.writeInt(player.getInventory().getSlotWithStack(itemStack));
            packetBuffer.writeItemStack(itemStack);

            ClientPlayNetworking.send(Paintbrush.SET_ITEMSTACK_PACKET_ID, packetBuffer);

            MinecraftClient.getInstance().inGameHud.heldItemTooltipFade = 40;

            ci.cancel();
        }
    }
}