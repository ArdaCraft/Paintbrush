package space.ajcool.paintbrush.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ajcool.paintbrush.Paintbrush;

import java.util.UUID;
@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Shadow @Final private BuiltinModelItemRenderer builtinModelItemRenderer;

    @Unique
    private static final ThreadLocal<Boolean> isRendering = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderItem(ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model, CallbackInfo ci) {

        // Avoid recursive rendering
        if (isRendering.get()) {
            return;
        }

        if (stack.isOf(Paintbrush.PAINTBRUSH_ITEM)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.isSneaking() && stack.hasNbt()) {
                UUID playerUUID = client.player.getUuid();
                boolean showInHand = Paintbrush.isHandToggleEnabled(playerUUID);
                NbtCompound paintNbt = stack.getSubNbt("paintbrush");

                if (paintNbt != null) {
                    ItemStack newStack = ItemStack.EMPTY;

                    if (paintNbt.contains("state")) {
                        NbtCompound stateNbt = paintNbt.getCompound("state");
                        String blockId = stateNbt.getString("Name");
                        Block block = Registries.BLOCK.get(new Identifier(blockId));
                        newStack = block.asItem().getDefaultStack();
                    } else if (paintNbt.contains("material")) {
                        String material = paintNbt.getString("material");
                        Identifier paintIdentifier = new Identifier(material);
                        Block block = Registries.BLOCK.get(paintIdentifier);
                        newStack = block.asItem().getDefaultStack();
                    }

                    // Ensure newStack is not air to prevent rendering loop
                    if (newStack.isEmpty()) {
                        return;
                    }

                    // Always render the paintbrush in hand unless /pb hand is toggled
                    if (renderMode == ModelTransformationMode.GUI || showInHand) {
                        BakedModel newModel = client.getItemRenderer().getModel(newStack, client.world, null, 0);

                        // Prevent subsequent calls from within this mixin
                        isRendering.set(true);
                        try {
                            ((ItemRenderer)(Object)this).renderItem(newStack, renderMode, leftHanded, matrices, vertexConsumers, light, overlay, newModel);
                        } finally {
                            isRendering.set(false);
                        }
                        ci.cancel();
                    }
                }
            }
        }
    }
}