package space.ajcool.paintbrush;

import com.conquestrefabricated.core.item.family.FamilyRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import space.ajcool.paintbrush.config.FullBlockMode;
import space.ajcool.paintbrush.config.PaintbrushConfig;
import space.ajcool.paintbrush.family.FamilyGroupRegistry;
import space.ajcool.paintbrush.item.PaintKnifeItem.LayerChangeOutcome;
import space.ajcool.paintbrush.item.PaintKnifeItem.LayerChangeResult;
import space.ajcool.paintbrush.render.PaintbrushHighlightRenderer;
import space.ajcool.paintbrush.state.PaintbrushKeys;
import space.ajcool.paintbrush.tokenizer.PaintbrushResourcesReloadListener;
import space.ajcool.paintbrush.tokenizer.TokenRegistry;

import static space.ajcool.paintbrush.Paintbrush.*;
import static space.ajcool.paintbrush.item.PaintKnifeItem.changeBlockLayer;
import static space.ajcool.paintbrush.item.PaintKnifeItem.reportDebugResult;

/**
 * Client-side mod initialization for Paintbrush.
 * Registers client-side event handlers for block interactions, renders, and commands.
 * Manages client configuration and resource reloading for tokens and filters.
 */
@Environment(EnvType.CLIENT)
public class PaintbrushClient implements ClientModInitializer {
    /**
     * Initializes the client-side mod components.
     * Registers attack block callbacks for paintbrush and paint knife interactions.
     * Registers client commands and sets up HUD rendering and resource listeners.
     */
    @Override
    public void onInitializeClient() {
        PaintbrushConfig.load();
        ClientPlayConnectionEvents.JOIN.register((_, _, client) ->
        {
            var player = client.player;
            if (player == null) return;

            syncBlockToggles(player);
        });
        PaintbrushKeys.register();

        EntityRenderers.register(Paintbrush.TOMATO, ThrownItemRenderer::new);

        AttackBlockCallback.EVENT.register((player, _, _, pos, direction) -> {
            if (player.isSpectator()) return InteractionResult.PASS;

            var itemStack = player.getMainHandItem();

            if (itemStack.getItem().equals(PAINTBRUSH_ITEM)) return handlePaintbrushInteraction(player, itemStack, pos);
            if (itemStack.getItem().equals(PAINT_KNIFE_ITEM))
                return handlePaintKnifeInteraction(player, itemStack, pos, direction);

            return InteractionResult.PASS;
        });

        // Registering client side commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
            configureClientCommand("paintbrush", dispatcher);
            configureClientCommand("pb", dispatcher);
            dispatcher.register(ClientCommands.literal("paintknife")
                    .executes(this::givePaintKnife)
                    .then(ClientCommands.literal("toggle")
                            .executes(this::togglePaintKnifeOperations))
                    .then(ClientCommands.literal("delete")
                            .executes(this::togglePaintKnifeDelete))
                    .then(ClientCommands.literal("append")
                            .executes(this::togglePaintKnifeAppend))
                    .then(ClientCommands.literal("settings")
                            .executes(this::showPaintKnifeSettings))
                    .then(ClientCommands.literal("fullblocks")
                            .executes(this::cyclePaintKnifeFullBlocks)
                            .then(ClientCommands.literal("all")
                                    .executes(ctx -> setFullBlocks(ctx, FullBlockMode.ALL)))
                            .then(ClientCommands.literal("partial")
                                    .executes(ctx -> setFullBlocks(ctx, FullBlockMode.PARTIAL)))
                            .then(ClientCommands.literal("none")
                                    .executes(ctx -> setFullBlocks(ctx, FullBlockMode.NONE))))
                    .then(ClientCommands.literal("debug")
                            .executes(this::togglePaintKnifeDebug)));
            dispatcher.register(ClientCommands.literal("pk")
                    .executes(this::givePaintKnife)
                    .then(ClientCommands.literal("toggle")
                            .executes(this::togglePaintKnifeOperations))
                    .then(ClientCommands.literal("delete")
                            .executes(this::togglePaintKnifeDelete))
                    .then(ClientCommands.literal("append")
                            .executes(this::togglePaintKnifeAppend))
                    .then(ClientCommands.literal("settings")
                            .executes(this::showPaintKnifeSettings))
                    .then(ClientCommands.literal("fullblocks")
                            .executes(this::cyclePaintKnifeFullBlocks)
                            .then(ClientCommands.literal("all")
                                    .executes(ctx -> setFullBlocks(ctx, FullBlockMode.ALL)))
                            .then(ClientCommands.literal("partial")
                                    .executes(ctx -> setFullBlocks(ctx, FullBlockMode.PARTIAL)))
                            .then(ClientCommands.literal("none")
                                    .executes(ctx -> setFullBlocks(ctx, FullBlockMode.NONE))))
                    .then(ClientCommands.literal("debug")
                            .executes(this::togglePaintKnifeDebug)));
        });

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(Paintbrush.ModID, "filter_indicator"),
                (extractor, _) -> {
                    if (!PaintbrushConfig.FILTER_FOLIAGE) return;
                    extractor.text(Minecraft.getInstance().font, Component.translatable("paintbrush.filtering_foliage"), 20, 20, 0xFFFFFFFF);
                });

        LevelRenderEvents.END_MAIN.register(PaintbrushHighlightRenderer::render);

        // Token management
        ResourceLoader.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(
                        Identifier.fromNamespaceAndPath("paintbrush", "resources_reload"),
                        new PaintbrushResourcesReloadListener());
    }

    /**
     * Handles primary attack with the paintbrush to copy a block material or state.
     * If Ctrl is held, copies the exact block state (strict mode); otherwise copies the block's family root.
     * Updates the paintbrush NBT and sends synchronization packet to the server.
     *
     * @param player    the player using the paintbrush
     * @param itemStack the paintbrush item stack
     * @param pos       the block position being clicked
     * @return FAIL to cancel the default attack behaviour
     */
    private InteractionResult handlePaintbrushInteraction(Player player, ItemStack itemStack, BlockPos pos) {
        var cooldownManager = player.getCooldowns();
        if (cooldownManager.isOnCooldown(itemStack)) return InteractionResult.CONSUME;

        var blockState = player.level().getBlockState(pos);
        var material = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());

        var iHaveAState = false;

        if (isControlDown()) {
            iHaveAState = true;
        } else {
            var paintFamily = FamilyRegistry.BLOCKS.getFamily(blockState.getBlock());

            if (!paintFamily.isAbsent() && !paintFamily.getMembers().isEmpty()) {
                blockState = paintFamily.getRoot().defaultBlockState();
            }
        }

        var copiedState = blockState;
        var strictMode = iHaveAState;
        PaintbrushData.mutate(itemStack, paintNbt ->
        {
            paintNbt.putString("material", material.toString());
            paintNbt.remove("state");
            if (strictMode) paintNbt.put("state", NbtUtils.writeBlockState(copiedState));
        });

        var lore = new java.util.ArrayList<Component>();
        lore.add(Component.literal(material.toString()).withStyle(ChatFormatting.BLUE));
        if (iHaveAState) {
            var stateString = blockState.toString().split("}");
            if (stateString.length > 1) {
                lore.add(Component.literal(stateString[1]).withStyle(ChatFormatting.BLUE));
            }
        }

        itemStack.set(DataComponents.LORE, new ItemLore(lore));
        itemStack.set(DataComponents.CUSTOM_NAME, PaintbrushNaming.buildBrushName(itemStack, BuiltInRegistries.BLOCK));

        player.getCooldowns().addCooldown(itemStack, 5);
        player.playSound(SoundEvents.SLIME_BLOCK_BREAK, 0.2F, 1.0F);

        player.getInventory().setChanged();

        Minecraft.getInstance().gui.toolHighlightTimer = 40;

        ClientPlayNetworking.send(new Paintbrush.SetItemStackPayload(itemStack));

        return InteractionResult.FAIL;
    }

    /**
     * Handles primary attack with the paint knife to decrement layer/level properties.
     * Decrements the targeted block's layer property by 1.
     *
     * @param player           the player using the paint knife
     * @param ignoredItemStack the paint knife item stack
     * @param pos              the block position being clicked
     * @param direction        the side of the block that was clicked
     * @return FAIL to cancel the default attack behaviour
     */
    @SuppressWarnings("SameReturnValue")
    private InteractionResult handlePaintKnifeInteraction(Player player, ItemStack ignoredItemStack, BlockPos pos, Direction direction) {
        if (player.getCooldowns().isOnCooldown(player.getMainHandItem())) {
            reportDebugResult(player, "cooling down", null);
            return InteractionResult.FAIL;
        }

        var result = changeBlockLayer(player, pos, direction, -1);
        if (result.outcome() == LayerChangeOutcome.SENT) {
            player.getCooldowns().addCooldown(player.getMainHandItem(), 4);
        }

        reportDebugResult(player, describePaintKnifeOutcome(result), result);

        return InteractionResult.FAIL;
    }

    /**
     * Mirrors the current block-toggle suppression setting into the local static map and syncs it to the server.
     * This keeps common interaction code consistent on both sides after join and after runtime config changes.
     *
     * @param player the player whose preference should be synchronized
     */
    private void syncBlockToggles(Player player) {
        Paintbrush.setBlockTogglesDisabled(player.getUUID(), PaintbrushConfig.DISABLE_BLOCK_TOGGLES);

        ClientPlayNetworking.send(new SetBlockTogglesPayload(PaintbrushConfig.DISABLE_BLOCK_TOGGLES));
    }

    /**
     * Registers client-side paintbrush commands with the given dispatcher.
     * Handles filter, block-toggle, and debug subcommands for runtime client preferences.
     *
     * @param commandName the command name to register (paintbrush or pb)
     * @param dispatcher  the command dispatcher to register with
     */
    private void configureClientCommand(String commandName, CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal(commandName)
                .then(ClientCommands.literal("filter")
                        .executes(this::toggleFilterFoliage))
                .then(ClientCommands.literal("blocktoggles")
                        .executes(this::toggleBlockToggles))
                .then(ClientCommands.literal("settings")
                        .executes(this::showPaintbrushSettings))
                .then(ClientCommands.literal("debug")
                        .executes(this::toggleTokenizerDebugOutput)
                        .then(ClientCommands.literal("showTokens")
                                .executes(this::showLoadedTokens))
                        .then(ClientCommands.literal("showFamily")
                                .executes(this::showFamily)))
        );
    }

    /**
     * Toggles the foliage filtering setting and saves the configuration.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int toggleFilterFoliage(CommandContext<FabricClientCommandSource> context) {
        PaintbrushConfig.FILTER_FOLIAGE = !PaintbrushConfig.FILTER_FOLIAGE;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        sendToggleMessage(player, settingLabel("paintbrush.setting.foliage_filtering"), PaintbrushConfig.FILTER_FOLIAGE);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Toggles block-toggle suppression, persists the setting, and re-syncs it to the server.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int toggleBlockToggles(CommandContext<FabricClientCommandSource> context) {
        PaintbrushConfig.DISABLE_BLOCK_TOGGLES = !PaintbrushConfig.DISABLE_BLOCK_TOGGLES;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        syncBlockToggles(player);
        sendToggleMessage(player, settingLabel("paintbrush.setting.block_toggle_suppression"), PaintbrushConfig.DISABLE_BLOCK_TOGGLES);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Toggles tokenizer debug output for the brush held in the main hand.
     * When enabled, tokenizer matching will be logged for debugging purposes.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int toggleTokenizerDebugOutput(CommandContext<FabricClientCommandSource> context) {

        var player = context.getSource().getPlayer();

        var itemStack = player.getMainHandItem();

        var paintNbt = PaintbrushData.read(itemStack);

        if (!paintNbt.contains("debug")) {
            PaintbrushData.mutate(itemStack, data -> data.putString("debug", "true"));

            var message = Component.empty()
                    .append(Component.literal("Debug output enabled").withStyle(ChatFormatting.DARK_AQUA));
            player.sendSystemMessage(message);
        } else {

            PaintbrushData.mutate(itemStack, data -> data.remove("debug"));

            var message = Component.empty()
                    .append(Component.literal("Debug output disabled").withStyle(ChatFormatting.RED));
            player.sendSystemMessage(message);
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Displays all loaded tokens and reserved names to the log.
     * Useful for debugging tokenizer matching behaviour.
     *
     * @param ignoredContext the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int showLoadedTokens(CommandContext<FabricClientCommandSource> ignoredContext) {
        StringBuilder builder = new StringBuilder("Paintbrush - Reserved names :\n");

        for (String reserved : TokenRegistry.RESERVED_TOKENS) {
            builder.append(reserved)
                    .append("\n");
        }

        LOGGER.info(builder.toString());

        builder = new StringBuilder("Paintbrush - Tokens :\n");

        for (String token : TokenRegistry.TOKENS) {
            builder.append(token)
                    .append("\n");
        }

        LOGGER.info(builder.toString());

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Shows information about the block family of the targeted block.
     * Displays the block ID, family root, family size, and family group.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int showFamily(CommandContext<FabricClientCommandSource> context) {
        var player = context.getSource().getPlayer();

        var client = Minecraft.getInstance();
        var hitResult = player.pick(20.0D, client.getDeltaTracker().getGameTimeDeltaPartialTick(false), false);

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            player.sendSystemMessage(Component.literal("Paintbrush: No block targeted.").withStyle(ChatFormatting.RED));
            return Command.SINGLE_SUCCESS;
        }

        var blockPos = ((BlockHitResult) hitResult).getBlockPos();
        var blockState = player.level().getBlockState(blockPos);
        var block = blockState.getBlock();
        var family = FamilyRegistry.BLOCKS.getFamily(block);
        var blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

        if (family == null || family.isAbsent()) {
            var message = Component.empty()
                    .append(Component.literal("Paintbrush: ").withStyle(ChatFormatting.DARK_AQUA))
                    .append(Component.literal(blockId + " has no family").withStyle(ChatFormatting.GRAY));

            player.sendSystemMessage(message);
            LOGGER.info("Paintbrush - Family debug: block={} family=none", blockId);
            return Command.SINGLE_SUCCESS;
        }

        var familyRootId = BuiltInRegistries.BLOCK.getKey(family.getRoot()).toString();
        var groupDescription = FamilyGroupRegistry.describe(block).orElse("none");

        var message = Component.empty()
                .append(Component.literal("Paintbrush: ").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(blockId + " family=" + familyRootId + " members=" + family.getMembers().size() + " group=" + groupDescription).withStyle(ChatFormatting.GRAY));

        player.sendSystemMessage(message);
        LOGGER.info("Paintbrush - Family debug: block={} family={} members={} group={}", blockId, familyRootId, family.getMembers().size(), groupDescription);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Shows the current paint knife settings to the player.
     *
     * @param player the player to notify
     */
    private void showPaintKnifeSettings(Player player) {
        sendToggleMessage(player, settingLabel("paintbrush.setting.paintknife_deletion"), PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE);
        sendToggleMessage(player, settingLabel("paintbrush.setting.paintknife_append"), PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND);
        sendValueMessage(player, settingLabel("paintbrush.setting.paintknife_fullblocks"), PaintbrushConfig.PAINTKNIFE_FULL_BLOCKS.name());
        sendToggleMessage(player, settingLabel("paintbrush.setting.paintknife_debug"), PaintbrushConfig.PAINTKNIFE_DEBUG);
    }

    /**
     * Shows the current paint knife settings to the player.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int showPaintKnifeSettings(CommandContext<FabricClientCommandSource> context) {
        var player = context.getSource().getPlayer();
        showPaintKnifeSettings(player);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Shows the current paintbrush settings to the player.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int showPaintbrushSettings(CommandContext<FabricClientCommandSource> context) {
        var player = context.getSource().getPlayer();

        sendToggleMessage(player, settingLabel("paintbrush.setting.foliage_filtering"), PaintbrushConfig.FILTER_FOLIAGE);
        sendToggleMessage(player, settingLabel("paintbrush.setting.block_toggle_suppression"), PaintbrushConfig.DISABLE_BLOCK_TOGGLES);

        var itemStack = player.getMainHandItem();
        if (itemStack.is(PAINTBRUSH_ITEM)) {
            var paintNbt = PaintbrushData.read(itemStack);
            var size = paintNbt.getIntOr("size", 1);
            var debug = paintNbt.contains("debug");

            sendValueMessage(player, settingLabel("paintbrush.setting.brush_size"), String.valueOf(size));
            sendToggleMessage(player, settingLabel("paintbrush.setting.brush_debug"), debug);
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Requests a paint knife from the server and prints the current settings.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int givePaintKnife(CommandContext<FabricClientCommandSource> context) {
        ClientPlayNetworking.send(new GivePaintKnifePayload());

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Toggles both paint knife operations and saves the configuration.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int togglePaintKnifeOperations(CommandContext<FabricClientCommandSource> context) {
        var enable = !(PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE && PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND);
        PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE = enable;
        PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND = enable;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        sendToggleMessage(player, settingLabel("paintbrush.setting.paintknife_deletion"), PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE);
        sendToggleMessage(player, settingLabel("paintbrush.setting.paintknife_append"), PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Toggles the paint knife block deletion setting and saves the configuration.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int togglePaintKnifeDelete(CommandContext<FabricClientCommandSource> context) {
        PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE = !PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        sendToggleMessage(player, settingLabel("paintbrush.setting.paintknife_deletion"), PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Toggles the paint knife block append setting and saves the configuration.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int togglePaintKnifeAppend(CommandContext<FabricClientCommandSource> context) {
        PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND = !PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        sendToggleMessage(player, settingLabel("paintbrush.setting.paintknife_append"), PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Toggles whether the paint knife promotes max-layer blocks to full blocks and saves the configuration.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int cyclePaintKnifeFullBlocks(CommandContext<FabricClientCommandSource> context) {
        PaintbrushConfig.PAINTKNIFE_FULL_BLOCKS = PaintbrushConfig.PAINTKNIFE_FULL_BLOCKS.next();
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        sendValueMessage(player, settingLabel("paintbrush.setting.paintknife_fullblocks"), PaintbrushConfig.PAINTKNIFE_FULL_BLOCKS.name());

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Sets the paint knife full-block promotion mode and saves the configuration.
     *
     * @param context the command context
     * @param mode    the full-block promotion mode to set
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int setFullBlocks(CommandContext<FabricClientCommandSource> context, FullBlockMode mode) {
        PaintbrushConfig.PAINTKNIFE_FULL_BLOCKS = mode;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        sendValueMessage(player, settingLabel("paintbrush.setting.paintknife_fullblocks"), PaintbrushConfig.PAINTKNIFE_FULL_BLOCKS.name());

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Toggles paint knife debug output and saves the configuration.
     *
     * @param context the command context
     * @return 1 if successful
     */
    @SuppressWarnings("SameReturnValue")
    private int togglePaintKnifeDebug(CommandContext<FabricClientCommandSource> context) {
        PaintbrushConfig.PAINTKNIFE_DEBUG = !PaintbrushConfig.PAINTKNIFE_DEBUG;
        PaintbrushConfig.save();

        var player = context.getSource().getPlayer();
        sendToggleMessage(player, settingLabel("paintbrush.setting.paintknife_debug"), PaintbrushConfig.PAINTKNIFE_DEBUG);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Converts a LayerChangeResult outcome into a human-readable string for debugging.
     *
     * @param result the layer change result to describe
     * @return a string representation of the outcome
     */
    private String describePaintKnifeOutcome(LayerChangeResult result) {
        return switch (result.outcome()) {
            case SENT -> "SENT";
            case NO_TARGET -> "NO_TARGET";
            case UNCHANGED -> "UNCHANGED";
            case OUT_OF_BOUNDS -> "OUT_OF_BOUNDS";
        };
    }

    private static boolean isControlDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL);
    }

    /**
     * Builds a translated label for a settings feedback message.
     *
     * @param key the translation key for the label
     * @return the formatted label
     */
    private Component settingLabel(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * Sends a formatted toggle state message to the player.
     *
     * @param player  the player to send the message to
     * @param label   the label describing what was toggled
     * @param enabled true if the feature is enabled, false otherwise
     */
    private void sendToggleMessage(Player player, Component label, boolean enabled) {
        var message = PaintbrushNaming.prefixedMessage(Component.translatable(
                enabled ? "paintbrush.settings.enabled" : "paintbrush.settings.disabled",
                label
        ).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));

        player.sendSystemMessage(message);
    }

    /**
     * Sends a formatted value state message to the player.
     *
     * @param player the player to send the message to
     * @param label  the label describing what was changed
     * @param value  the new value being displayed
     */
    @SuppressWarnings("SameParameterValue")
    private void sendValueMessage(Player player, Component label, String value) {
        var message = PaintbrushNaming.prefixedMessage(Component.translatable(
                "paintbrush.settings.value",
                label,
                Component.literal(value).withStyle(ChatFormatting.AQUA)
        ).withStyle(ChatFormatting.DARK_GRAY));

        player.sendSystemMessage(message);
    }
}
