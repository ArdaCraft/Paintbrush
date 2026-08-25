package space.ajcool.paintbrush.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.PaintbrushData;

/**
 * Mixin for ItemModelResolver to render the painted material block in the player's hand and GUI.
 * When holding a paintbrush with a copied material and shift is held, displays the material block
 * instead of the paintbrush item. Injects at the render-state population seam so the swap happens
 * before the state is built — cleaner than the old BakedModel-era approach.
 */
@Environment(EnvType.CLIENT)
@Mixin(ItemModelResolver.class)
public abstract class ItemRendererMixin {

    /** Thread-local flag to prevent recursive render-state updates. */
    @Unique
    private static final ThreadLocal<Boolean> isUpdating = ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract void updateForTopItem(ItemStackRenderState output, ItemStack stack,
            ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed);

    @Inject(method = "updateForTopItem", at = @At("HEAD"), cancellable = true)
    private void paintbrush$renderMaterial(
            ItemStackRenderState output,
            ItemStack item,
            ItemDisplayContext displayContext,
            Level level,
            ItemOwner owner,
            int seed,
            CallbackInfo ci) {

        if (isUpdating.get()) return;
        if (!item.is(Paintbrush.PAINTBRUSH_ITEM)) return;

        var client = Minecraft.getInstance();
        if (client.player == null || !client.player.isShiftKeyDown()) return;

        var paintNbt = PaintbrushData.read(item);
        if (paintNbt.isEmpty()) return;

        var uuid = client.player.getUUID();
        var showInHand = Paintbrush.isHandToggleEnabled(uuid);

        boolean isGui = displayContext == ItemDisplayContext.GUI;
        if (!isGui && !showInHand) return;

        ItemStack materialStack = resolveMaterialStack(paintNbt);
        if (materialStack.isEmpty()) return;

        isUpdating.set(true);
        try {
            updateForTopItem(output, materialStack, displayContext, level, owner, seed);
        } finally {
            isUpdating.set(false);
        }
        ci.cancel();
    }

    @Unique
    private static ItemStack resolveMaterialStack(net.minecraft.nbt.CompoundTag paintNbt) {
        if (paintNbt.contains("state")) {
            var stateNbt = paintNbt.getCompound("state").orElseGet(net.minecraft.nbt.CompoundTag::new);
            var blockId = stateNbt.getStringOr("Name", "");
            if (blockId.isEmpty()) return ItemStack.EMPTY;
            var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
            return block.asItem().getDefaultInstance();
        } else if (paintNbt.contains("material")) {
            var material = paintNbt.getStringOr("material", "");
            if (material.isEmpty()) return ItemStack.EMPTY;
            var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(material));
            return block.asItem().getDefaultInstance();
        }
        return ItemStack.EMPTY;
    }
}
