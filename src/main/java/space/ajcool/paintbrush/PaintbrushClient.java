package space.ajcool.paintbrush;

import com.conquestrefabricated.core.item.family.FamilyRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import space.ajcool.paintbrush.state.PaintbrushKeys;
import space.ajcool.paintbrush.state.PaintbrushState;
import space.ajcool.paintbrush.tokenizer.TokenRegistry;
import space.ajcool.paintbrush.tokenizer.PaintbrushResourcesReloadListener;

import static space.ajcool.paintbrush.Paintbrush.*;
import static space.ajcool.paintbrush.item.PaintKnifeItem.changeBlockLayer;

@Environment(EnvType.CLIENT)
public class PaintbrushClient implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
		EntityRendererRegistry.register(Paintbrush.TOMATO, FlyingItemEntityRenderer::new);

		PaintbrushKeys.register();

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.isSpectator()) return ActionResult.PASS;

			var itemStack = player.getMainHandStack();

			if (itemStack.getItem().equals(PAINTBRUSH_ITEM)) return handlePaintbrushInteraction(player, itemStack, pos);
			if (itemStack.getItem().equals(PAINT_KNIFE_ITEM)) return handlePaintKnifeInteraction(player, itemStack, pos, direction);

			return ActionResult.PASS;
		});

        // Registering client side commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            configureClientCommand("paintbrush", dispatcher);
            configureClientCommand("pb", dispatcher);
        });

        // Token management
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new PaintbrushResourcesReloadListener());

		HudRenderCallback.EVENT.register((context, tickDelta) -> {
			if (PaintbrushState.DISABLE_FOLIAGE_PAINT) {

				MinecraftClient client = MinecraftClient.getInstance();
				TextRenderer textRenderer = client.textRenderer;

				context.drawText(textRenderer, Text.translatable("paintbrush.filtering_foliage"), 20, 20, 0xFFFFFF, true);
			}
		});
	}

	private ActionResult handlePaintbrushInteraction(PlayerEntity player, ItemStack itemStack, BlockPos pos)
	{
		var cooldownManager = player.getItemCooldownManager();
		if (cooldownManager.isCoolingDown(PAINTBRUSH_ITEM)) return ActionResult.CONSUME;

		var blockState = player.getWorld().getBlockState(pos);
		var material = Registries.BLOCK.getId(blockState.getBlock());

		// Set internal NBT for block material and state
		var paintNbt = itemStack.getOrCreateSubNbt("paintbrush");
		paintNbt.put("material", NbtString.of(material.toString()));

		var iHaveAState = false;

		if (paintNbt.contains("state")) paintNbt.remove("state");
		if (Screen.hasControlDown())
		{
			paintNbt.put("state", NbtHelper.fromBlockState(blockState));
			iHaveAState = true;
		}
		else blockState = FamilyRegistry.BLOCKS.getFamily(material).getRoot().getDefaultState();

		// Set display.Lore NBT so values are viewable in inventory
		var displayNbt = itemStack.getOrCreateSubNbt("display");
		var loreList = displayNbt.getList("Lore", 8);

		loreList.clear();
		loreList.add(NbtString.of("{\"color\":\"blue\", \"text\":\"" + material + "\"}"));

		if (iHaveAState)
		{
			var stateString = blockState.toString().split("}");
			if (stateString.length > 1)
			{
				loreList.add(NbtString.of("{\"color\":\"blue\", \"text\":\"" + stateString[1] + "\"}"));
			}
		}

		displayNbt.put("Lore", loreList);

		// Set Item name
		var localName = Text.translatable(blockState.getBlock().getTranslationKey());
		var name = Text.empty()
				.append(localName)
				.append(" Paintbrush")
				.formatted(iHaveAState ? Formatting.RED : Formatting.AQUA);

		if (paintNbt.contains("size"))
		{
			var size =  paintNbt.getInt("size");
			if (size > 1) name.append(Text.literal(" (" + size + ")").formatted(Formatting.GRAY));
		}

		itemStack.setCustomName(name);

		player.getItemCooldownManager().set(PAINTBRUSH_ITEM, 5);
		player.playSound(SoundEvents.BLOCK_SLIME_BLOCK_BREAK, 0.2F, 1.0F);

		player.getInventory().markDirty();

		MinecraftClient.getInstance().inGameHud.heldItemTooltipFade = 40;

		var packetBuffer = PacketByteBufs.create();
		packetBuffer.writeInt(player.getInventory().getSlotWithStack(itemStack));
		packetBuffer.writeItemStack(itemStack);

		ClientPlayNetworking.send(Paintbrush.SET_ITEMSTACK_PACKET_ID, packetBuffer);

		return ActionResult.FAIL;
	}

	private ActionResult handlePaintKnifeInteraction(PlayerEntity player, ItemStack itemStack, BlockPos pos, Direction direction)
	{
		if (player.getItemCooldownManager().isCoolingDown(PAINT_KNIFE_ITEM)) return ActionResult.FAIL;
		player.getItemCooldownManager().set(PAINT_KNIFE_ITEM, 4);

		changeBlockLayer(player, pos, direction, -1);

		return ActionResult.FAIL;
	}

    private void configureClientCommand(String commandName, CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal(commandName)
                .then(ClientCommandManager.literal("debug")
                        .executes(this::toggleTokenizerDebugOutput)
                    .then(ClientCommandManager.literal("showTokens")
                            .executes(this::showLoadedTokens)))
        );
    }

    private int showLoadedTokens(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException
    {
        StringBuilder builder = new StringBuilder("Paintbrush - Reserved names :\n");

        for (String reserved : TokenRegistry.RESERVED_TOKENS)
        {
            builder.append(reserved)
                    .append("\n");
        }

        LOGGER.info(builder.toString());

        builder = new StringBuilder("Paintbrush - Tokens :\n");

        for (String token : TokenRegistry.TOKENS)
        {
            builder.append(token)
                    .append("\n");
        }

        LOGGER.info(builder.toString());

        return Command.SINGLE_SUCCESS;
    }

    private int toggleTokenizerDebugOutput(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException
    {

        var player = context.getSource().getPlayer();

        if (player != null)
        {
            var itemStack = player.getMainHandStack();

            // Set internal NBT for block material and state
            var paintNbt = itemStack.getOrCreateSubNbt("paintbrush");

            if (!paintNbt.contains("debug")) {
                paintNbt.put("debug", NbtString.of("true"));

                var message = Text.empty()
                        .append(Text.literal("Debug output enabled").formatted(Formatting.DARK_AQUA));
                player.sendMessage(message);
            } else {

                paintNbt.remove("debug");

                var message = Text.empty()
                        .append(Text.literal("Debug output disabled").formatted(Formatting.RED));
                player.sendMessage(message);
            }
        }

        return Command.SINGLE_SUCCESS;
    }
}