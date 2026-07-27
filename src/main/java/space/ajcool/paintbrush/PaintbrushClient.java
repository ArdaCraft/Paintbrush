package space.ajcool.paintbrush;

import com.conquestrefabricated.core.item.family.FamilyRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import space.ajcool.paintbrush.config.PaintbrushConfig;
import space.ajcool.paintbrush.family.FamilyGroupRegistry;
import space.ajcool.paintbrush.render.PaintbrushHighlightRenderer;
import space.ajcool.paintbrush.state.PaintbrushKeys;
import space.ajcool.paintbrush.tokenizer.PaintbrushResourcesReloadListener;
import space.ajcool.paintbrush.tokenizer.TokenRegistry;

import static space.ajcool.paintbrush.Paintbrush.*;
import static space.ajcool.paintbrush.item.PaintKnifeItem.changeBlockLayer;

@Environment(EnvType.CLIENT)
public class PaintbrushClient implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
        PaintbrushConfig.load();
        PaintbrushKeys.register();

		EntityRendererRegistry.register(Paintbrush.TOMATO, FlyingItemEntityRenderer::new);

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
            configurePaintKnifeCommand("paintknife", dispatcher);
            configurePaintKnifeCommand("pk", dispatcher);
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) ->
        {
            if (!PaintbrushConfig.FILTER_FOLIAGE) return;

            drawContext.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("paintbrush.filtering_foliage"), 20, 20, 0xFFFFFFFF, true);
        });

        WorldRenderEvents.LAST.register(PaintbrushHighlightRenderer::render);

        // Token management
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new PaintbrushResourcesReloadListener());
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
                .then(ClientCommandManager.literal("filter")
                        .executes(this::showFilterState)
                    .then(ClientCommandManager.literal("toggle")
                            .executes(this::toggleFilterFoliage)))
                .then(ClientCommandManager.literal("debug")
                        .executes(this::toggleTokenizerDebugOutput)
                    .then(ClientCommandManager.literal("showTokens")
                            .executes(this::showLoadedTokens))
                    .then(ClientCommandManager.literal("showFamily")
                            .executes(this::showFamily)))
        );
    }

    private int showFilterState(CommandContext<FabricClientCommandSource> context)
    {
        var player = context.getSource().getPlayer();

        if (player != null)
        {
            sendToggleMessage(player, "Foliage filtering", PaintbrushConfig.FILTER_FOLIAGE);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int toggleFilterFoliage(CommandContext<FabricClientCommandSource> context)
    {
        PaintbrushConfig.FILTER_FOLIAGE = !PaintbrushConfig.FILTER_FOLIAGE;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        if (player != null)
        {
            sendToggleMessage(player, "Foliage filtering", PaintbrushConfig.FILTER_FOLIAGE);
        }

        return Command.SINGLE_SUCCESS;
    }

    private void configurePaintKnifeCommand(String commandName, CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal(commandName)
                .executes(this::showPaintKnifeSettings)
                .then(ClientCommandManager.literal("allow")
                        .then(ClientCommandManager.literal("delete")
                                .executes(this::togglePaintKnifeDelete))
                        .then(ClientCommandManager.literal("append")
                                .executes(this::togglePaintKnifeAppend)))
        );
    }

    private int showPaintKnifeSettings(CommandContext<FabricClientCommandSource> context)
    {
        var player = context.getSource().getPlayer();

        if (player != null)
        {
            sendToggleMessage(player, "Paintknife block deletion", PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE);
            sendToggleMessage(player, "Paintknife block append", PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int togglePaintKnifeDelete(CommandContext<FabricClientCommandSource> context)
    {
        PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE = !PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        if (player != null)
        {
            sendToggleMessage(player, "Paintknife block deletion", PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int togglePaintKnifeAppend(CommandContext<FabricClientCommandSource> context)
    {
        PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND = !PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        if (player != null)
        {
            sendToggleMessage(player, "Paintknife block append", PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND);
        }

        return Command.SINGLE_SUCCESS;
    }

    private void sendToggleMessage(PlayerEntity player, String label, boolean enabled)
    {
        var message = Text.empty()
                .append(Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                .append(Text.literal(label + " ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(enabled ? "enabled" : "disabled").formatted(enabled ? Formatting.GREEN : Formatting.RED));

        player.sendMessage(message);
    }

    private int showLoadedTokens(CommandContext<FabricClientCommandSource> context)
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

    private int showFamily(CommandContext<FabricClientCommandSource> context)
    {
        var player = context.getSource().getPlayer();
        if (player == null) return Command.SINGLE_SUCCESS;

        var client = MinecraftClient.getInstance();
        var hitResult = player.raycast(20.0D, client.getTickDelta(), false);

        if (hitResult.getType() != HitResult.Type.BLOCK)
        {
            player.sendMessage(Text.literal("Paintbrush: No block targeted.").formatted(Formatting.RED));
            return Command.SINGLE_SUCCESS;
        }

        var blockPos = ((BlockHitResult) hitResult).getBlockPos();
        var blockState = player.getWorld().getBlockState(blockPos);
        var block = blockState.getBlock();
        var family = FamilyRegistry.BLOCKS.getFamily(block);
        var blockId = Registries.BLOCK.getId(block).toString();

        if (family == null)
        {
            var message = Text.empty()
                    .append(Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                    .append(Text.literal(blockId + " has no family").formatted(Formatting.GRAY));

            player.sendMessage(message);
            LOGGER.info("Paintbrush - Family debug: block={} family=none", blockId);
            return Command.SINGLE_SUCCESS;
        }

        var familyRootId = Registries.BLOCK.getId(family.getRoot()).toString();
        var groupDescription = FamilyGroupRegistry.describe(block).orElse("none");

        var message = Text.empty()
                .append(Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                .append(Text.literal(blockId + " family=" + familyRootId + " members=" + family.getMembers().size() + " group=" + groupDescription).formatted(Formatting.GRAY));

        player.sendMessage(message);
        LOGGER.info("Paintbrush - Family debug: block={} family={} members={} group={}", blockId, familyRootId, family.getMembers().size(), groupDescription);

        return Command.SINGLE_SUCCESS;
    }

    private int toggleTokenizerDebugOutput(CommandContext<FabricClientCommandSource> context)
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
