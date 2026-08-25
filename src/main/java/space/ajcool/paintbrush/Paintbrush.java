package space.ajcool.paintbrush;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Pair;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.ajcool.paintbrush.entity.TomatoEntity;
import space.ajcool.paintbrush.item.PaintKnifeItem;
import space.ajcool.paintbrush.item.PaintbrushItem;
import space.ajcool.paintbrush.item.TomatoItem;

import java.util.*;

/**
 * Main mod initialization class for the Paintbrush mod.
 * Handles registration of items, entities, item groups, network packets, and commands.
 * Provides hand rendering toggle functionality for players and integrates with WorldEdit
 * for undo/redo support on paintbrush edits.
 */
public class Paintbrush implements ModInitializer {
    /** The mod's unique identifier used for items, packets, and namespacing. */
    public static final String ModID = "paintbrush";

    /** Logger instance for debugging and error reporting. */
    public static final Logger LOGGER = LoggerFactory.getLogger("paintbrush");

    /** The Tomato entity type used for thrown tomato projectiles. */
    public static final ResourceKey<EntityType<?>> TOMATO_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ModID, "tomato"));

    /** Registry key for the Paintbrush item. */
    public static final ResourceKey<Item> PAINTBRUSH_ITEM_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ModID, "paintbrush"));

    /** Registry key for the Paint Knife item. */
    public static final ResourceKey<Item> PAINT_KNIFE_ITEM_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ModID, "paint_knife"));

    /** Registry key for the Tomato item. */
    public static final ResourceKey<Item> TOMATO_ITEM_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ModID, "tomato"));

    /** The Tomato entity type used for thrown tomato projectiles. */
    public static final EntityType<TomatoEntity> TOMATO = EntityType.Builder.<TomatoEntity>of(TomatoEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(4)
            .updateInterval(10)
            .build(TOMATO_KEY);

    /** The Paintbrush item - used to copy and paint block materials across Conquest families. */
    public static final Item PAINTBRUSH_ITEM = new PaintbrushItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC).setId(PAINTBRUSH_ITEM_KEY));

    /** The Paint Knife item - used to modify layer-like block properties. */
    public static final Item PAINT_KNIFE_ITEM = new PaintKnifeItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC).setId(PAINT_KNIFE_ITEM_KEY));

    /** The Tomato item - a throwable food item. */
    public static final Item TOMATO_ITEM = new TomatoItem(new Item.Properties().stacksTo(16).setId(TOMATO_ITEM_KEY));

    /** Packet identifier for the set block network packet sent from client to server. */
    public static final Identifier SET_BLOCK_PACKET_ID = Identifier.fromNamespaceAndPath(ModID, "set_block");

    /** Packet identifier for the set itemstack network packet sent from client to server. */
    public static final Identifier SET_ITEMSTACK_PACKET_ID = Identifier.fromNamespaceAndPath(ModID, "set_itemstack");

    /** Packet identifier for the client request to give a paint knife. */
    public static final Identifier GIVE_PAINT_KNIFE_PACKET_ID = Identifier.fromNamespaceAndPath(ModID, "give_paint_knife");

    /** Packet identifier for synchronizing block toggle suppression from the client to the server. */
    public static final Identifier SET_BLOCK_TOGGLES_PACKET_ID = Identifier.fromNamespaceAndPath(ModID, "set_block_toggles");

    /** Registry key for the custom Paintbrush item group. */
    public static final ResourceKey<CreativeModeTab> PAINTBRUSH_ITEM_GROUP_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(ModID, "item_group"));

    /** Maximum number of blocks that can be sent in a single packet to prevent memory issues. */
    private static final int MAX_BLOCKS_PER_PACKET = 8192;

    private static final StreamCodec<RegistryFriendlyByteBuf, Pair<BlockPos, CompoundTag>> BLOCK_UPDATE_STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            Pair::getFirst,
            ByteBufCodecs.COMPOUND_TAG,
            Pair::getSecond,
            Pair::of
    );

    /** The custom Paintbrush item group containing all mod items. */
    private static final CreativeModeTab PAINTBRUSH_ITEM_GROUP = FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(PAINTBRUSH_ITEM))
            .title(Component.translatable("itemGroup.paintbrush.paintbrush"))
            .build();

    /** Maps player UUIDs to their hand rendering toggle state (true = show in hand, false = hotbar only). */
    private static final Map<UUID, Boolean> handToggle = new HashMap<>();

    /** Maps player UUIDs to their block toggle suppression state. */
    private static final Map<UUID, Boolean> blockTogglesDisabled = new HashMap<>();

    /**
     * Checks if block toggle suppression is enabled for the specified player.
     *
     * @param playerUUID the UUID of the player to check
     * @return true if block activation is suppressed, false otherwise
     */
    public static boolean isBlockTogglesDisabled(UUID playerUUID) {
        return blockTogglesDisabled.getOrDefault(playerUUID, false);
    }

    /**
     * Initializes the mod by registering items, entities, item groups, network handlers, and commands.
     * Sets up attack block callbacks to prevent breaking blocks with the paintbrush.
     * Registers network packet handlers for block painting and item stack synchronization.
     */
    @SuppressWarnings("resource")
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(SetBlockPayload.TYPE, SetBlockPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetItemStackPayload.TYPE, SetItemStackPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetBlockTogglesPayload.TYPE, SetBlockTogglesPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GivePaintKnifePayload.TYPE, GivePaintKnifePayload.STREAM_CODEC);

        // Registering all the items and groups
        Registry.register(BuiltInRegistries.ENTITY_TYPE, TOMATO_KEY, TOMATO);
        Registry.register(BuiltInRegistries.ITEM, PAINTBRUSH_ITEM_KEY, PAINTBRUSH_ITEM);
        Registry.register(BuiltInRegistries.ITEM, PAINT_KNIFE_ITEM_KEY, PAINT_KNIFE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TOMATO_ITEM_KEY, TOMATO_ITEM);

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, PAINTBRUSH_ITEM_GROUP_KEY, PAINTBRUSH_ITEM_GROUP);

        // Adding items to their respective groups
        CreativeModeTabEvents.modifyOutputEvent(PAINTBRUSH_ITEM_GROUP_KEY).register(itemGroup ->
        {
            itemGroup.accept(PAINTBRUSH_ITEM.getDefaultInstance());
            itemGroup.accept(PAINT_KNIFE_ITEM.getDefaultInstance());
            var airBrush = PAINTBRUSH_ITEM.getDefaultInstance();
            PaintbrushData.mutate(airBrush, paintNbt -> paintNbt.put("state", NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState())));
            var name = Component.translatable("paintbrush.item.name", Component.translatable(Blocks.AIR.getDescriptionId())).withStyle(ChatFormatting.RED);
            airBrush.set(DataComponents.CUSTOM_NAME, name);
            itemGroup.accept(airBrush);
            itemGroup.accept(TOMATO_ITEM.getDefaultInstance());
        });

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(itemGroup ->
        {
            itemGroup.accept(PAINTBRUSH_ITEM);
            itemGroup.accept(PAINT_KNIFE_ITEM);
        });

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(itemGroup -> itemGroup.accept(TOMATO_ITEM));

        // Handle block attacks and brush block interactions
        AttackBlockCallback.EVENT.register((player, world, _, _, _) ->
        {
            if (world.isClientSide()) return InteractionResult.PASS;

            var itemStack = player.getMainHandItem();
            if (!itemStack.is(PAINTBRUSH_ITEM)) return InteractionResult.PASS;

            return InteractionResult.FAIL; // Prevents breaking blocks with paintbrush
        });

        // Networking packets for multiplayer block setting
        ServerPlayNetworking.registerGlobalReceiver(SetBlockPayload.TYPE, (payload, context) ->
        {
            var server = context.server();
            var player = context.player();
            var queuedBlocks = payload.blocks();
            var blocksInPacket = queuedBlocks.size();
            if (blocksInPacket > MAX_BLOCKS_PER_PACKET) {
                LOGGER.warn("Dropping malformed paintbrush:set_block packet from {} with {} blocks", player.getName().getString(), blocksInPacket);
                return;
            }

            if (queuedBlocks.isEmpty()) return;

            server.execute(() ->
            {
                var world = player.level();

                HolderGetter<Block> registryEntryLookup = world.registryAccess().lookupOrThrow(Registries.BLOCK);
                var brushedBlocks = new LinkedHashMap<BlockPos, BlockState>();

                for (var queuedBlock : queuedBlocks) {
                    var blockState = NbtUtils.readBlockState(registryEntryLookup, queuedBlock.getSecond());
                    brushedBlocks.put(queuedBlock.getFirst(), blockState);

                    var blockEntity = world.getBlockEntity(queuedBlock.getFirst());
                    var canBreak = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, queuedBlock.getFirst(), blockState, blockEntity);
                    if (!canBreak) {
                        LOGGER.warn("Blocked paintbrush:set_block for player={} pos={} state={}",
                                player.getName().getString(), queuedBlock.getFirst(), blockState);
                        player.sendSystemMessage(PaintbrushNaming.prefixedMessage(
                                Component.translatable("paintbrush.message.blocked_by_protection", queuedBlock.getFirst().toShortString())
                                        .withStyle(ChatFormatting.DARK_GRAY)));
                        return;
                    }
                }

                var adapter = FabricAdapter.get();
                var actor = adapter.fromNativePlayer(player);
                LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);

                if (session == null) {
                    var firstEntry = brushedBlocks.entrySet().iterator().next();
                    LOGGER.warn("Dropped paintbrush:set_block for player={} reason=no_worldedit_session pos={} state={}",
                            player.getName().getString(), firstEntry.getKey(), firstEntry.getValue());
                    player.sendSystemMessage(PaintbrushNaming.prefixedMessage(
                            Component.translatable("paintbrush.message.no_worldedit_session")
                                    .withStyle(ChatFormatting.DARK_GRAY)));
                    return;
                }

                try (EditSession editSession = session.createEditSession(actor)) {
                    var firstEntry = brushedBlocks.entrySet().iterator().next();
                    LOGGER.debug("Applying paintbrush:set_block batch player={} count={} firstPos={}",
                            player.getName().getString(), brushedBlocks.size(), firstEntry.getKey());

                    for (Map.Entry<BlockPos, BlockState> kvpEntry : brushedBlocks.entrySet()) {
                        editSession.setBlock(adapter.adapt(kvpEntry.getKey()), adapter.fromNativeBlockState(kvpEntry.getValue()));
                    }

                    session.remember(editSession);
                } catch (MaxChangedBlocksException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        // Networking for item stack changes
        ServerPlayNetworking.registerGlobalReceiver(SetItemStackPayload.TYPE, (payload, context) ->
        {
            var player = context.player();
            var itemstack = payload.stack();

            if (itemstack == null) return;

            context.server().execute(() ->
                    player.getInventory().setItem(player.getInventory().getSelectedSlot(), itemstack));
        });

        ServerPlayNetworking.registerGlobalReceiver(SetBlockTogglesPayload.TYPE, (payload, context) ->
                context.server().execute(() -> setBlockTogglesDisabled(context.player().getUUID(), payload.disabled())));

        ServerPlayNetworking.registerGlobalReceiver(GivePaintKnifePayload.TYPE, (_, context) ->
                context.server().execute(() ->
                {
                    var player = context.player();
                    giveItem(player, PAINT_KNIFE_ITEM);
                    player.sendSystemMessage(PaintbrushNaming.prefixedMessage(
                            Component.translatable("paintbrush.message.paint_knife_added")
                                    .withStyle(ChatFormatting.DARK_GRAY)));
                }));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, _) ->
        {
            var playerUuid = handler.player.getUUID();
            handToggle.remove(playerUuid);
            blockTogglesDisabled.remove(playerUuid);
        });

        // Registering commands
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) ->
        {
            configureCommand("paintbrush", dispatcher);
            configureCommand("pb", dispatcher);
        });
    }

    public record SetBlockPayload(List<Pair<BlockPos, CompoundTag>> blocks) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetBlockPayload> TYPE = new CustomPacketPayload.Type<>(SET_BLOCK_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SetBlockPayload> STREAM_CODEC = StreamCodec.composite(
                BLOCK_UPDATE_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BLOCKS_PER_PACKET)),
                SetBlockPayload::blocks,
                SetBlockPayload::new
        );

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record SetItemStackPayload(ItemStack stack) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetItemStackPayload> TYPE = new CustomPacketPayload.Type<>(SET_ITEMSTACK_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SetItemStackPayload> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC,
                SetItemStackPayload::stack,
                SetItemStackPayload::new
        );

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record SetBlockTogglesPayload(boolean disabled) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<SetBlockTogglesPayload> TYPE = new CustomPacketPayload.Type<>(SET_BLOCK_TOGGLES_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SetBlockTogglesPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                SetBlockTogglesPayload::disabled,
                SetBlockTogglesPayload::new
        );

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    public record GivePaintKnifePayload() implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<GivePaintKnifePayload> TYPE = new CustomPacketPayload.Type<>(GIVE_PAINT_KNIFE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, GivePaintKnifePayload> STREAM_CODEC = StreamCodec.unit(new GivePaintKnifePayload());

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * Sets the block toggle suppression state for the specified player.
     * When disabled, holding the paintbrush or paint knife will not activate blocks.
     *
     * @param playerUUID the UUID of the player
     * @param disabled   true to suppress block activation, false to allow it
     */
    public static void setBlockTogglesDisabled(UUID playerUUID, boolean disabled) {
        blockTogglesDisabled.put(playerUUID, disabled);
    }

    /**
     * Adds a new mod item to the player's inventory.
     *
     * @param player the server player
     * @param item   the item type to create
     */
    public static void giveItem(ServerPlayer player, Item item) {
        player.getInventory().add(item.getDefaultInstance());
    }

    /**
     * Registers paintbrush commands with the given dispatcher.
     * Registers both "paintbrush" and "pb" as command aliases with the same structure.
     * Stubs certain commands (filter, blocktoggles, debug) that are implemented client-side.
     *
     * @param commandName the name of the command to register
     * @param dispatcher  the command dispatcher to register with
     */
    private void configureCommand(String commandName, CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(commandName)
                .executes(this::PaintBrushCommand)
                .then(Commands.literal("hand")
                        .executes(this::handleHandToggleCommand))
                .then(Commands.literal("size")
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(this::setBrushSize)))
                .then(Commands.literal("filter")
                        .executes(_ -> 1))
                .then(Commands.literal("blocktoggles")
                        .executes(_ -> 1))
                .then(Commands.literal("settings")
                        .executes(_ -> 1))
                .then(Commands.literal("debug")
                        .executes(_ -> 1)
                        .then(Commands.literal("showTokens")
                                .executes(_ -> 1))
                        .then(Commands.literal("showFamily")
                                .executes(_ -> 1)))
        );
    }

    /**
     * Handles the /pb command to give the player a dry paintbrush.
     * The dry paintbrush has no material selected and is ready to copy a block.
     *
     * @param context the command context containing the player and server information
     * @return 1 if the command succeeded, 0 otherwise
     */
    @SuppressWarnings("SameReturnValue")
    private int PaintBrushCommand(CommandContext<CommandSourceStack> context) {
        var player = context.getSource().getPlayer();

        if (player != null) {
            giveItem(player, PAINTBRUSH_ITEM);
            player.sendSystemMessage(PaintbrushNaming.prefixedMessage(
                    Component.translatable("paintbrush.message.paintbrush_added")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the /pb hand command to toggle hand rendering for the player.
     * Sends a feedback message to the player confirming the new state.
     *
     * @param context the command context containing the player and server information
     * @return 1 if the command succeeded, 0 otherwise
     */
    @SuppressWarnings("SameReturnValue")
    private int handleHandToggleCommand(CommandContext<CommandSourceStack> context) {
        var player = context.getSource().getPlayer();

        if (player != null) {
            UUID playerUUID = player.getUUID();
            toggleHandMode(playerUUID);

            boolean isHandEnabled = isHandToggleEnabled(playerUUID);
            player.sendSystemMessage(PaintbrushNaming.prefixedMessage(
                    Component.translatable(isHandEnabled ? "paintbrush.message.hand_shown" : "paintbrush.message.hand_hotbar")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the /pb size command to set the brush size for the player.
     * Size must be between 1 and 5 inclusive. Updates the brush item name and lore accordingly.
     * If the brush is in strict mode (Ctrl-copied), the name appears in red; otherwise, aqua.
     *
     * @param context the command context containing the player and server information
     * @return 1 if the command succeeded and size was valid, 0 if size was invalid or no brush held
     */
    private int setBrushSize(CommandContext<CommandSourceStack> context) {
        var player = context.getSource().getPlayer();

        if (player != null) {
            int size = IntegerArgumentType.getInteger(context, "value");

            if (size > 5 || size < 1) {
                player.sendSystemMessage(PaintbrushNaming.prefixedMessage(
                        Component.translatable("paintbrush.message.size_out_of_range")
                                .withStyle(ChatFormatting.RED)));

                return 0;
            }

            var itemStack = player.getInventory().getSelectedItem();

            if (!itemStack.is(PAINTBRUSH_ITEM)) {
                player.sendSystemMessage(PaintbrushNaming.prefixedMessage(
                        Component.translatable("paintbrush.message.size_needs_brush")
                                .withStyle(ChatFormatting.RED)));

                return 0;
            }

            PaintbrushData.mutate(itemStack, paintNbt -> paintNbt.putInt("size", size));

            player.level();
            HolderGetter<Block> registryEntryLookup = player.level().registryAccess().lookupOrThrow(Registries.BLOCK);
            itemStack.set(DataComponents.CUSTOM_NAME, PaintbrushNaming.buildBrushName(itemStack, registryEntryLookup));

            context.getSource().getServer().execute(() -> player.getInventory().setItem(player.getInventory().getSelectedSlot(), itemStack));

            player.sendSystemMessage(PaintbrushNaming.prefixedMessage(
                    Component.translatable("paintbrush.message.size_set", Component.literal(String.valueOf(size)).withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Toggles the hand rendering mode for the specified player.
     *
     * @param playerUUID the UUID of the player whose hand mode should be toggled
     */
    public static void toggleHandMode(UUID playerUUID) {
        handToggle.put(playerUUID, !isHandToggleEnabled(playerUUID));
    }

    /**
     * Checks if hand rendering is enabled for the specified player.
     *
     * @param playerUUID the UUID of the player to check
     * @return true if hand rendering is enabled, false otherwise
     */
    public static boolean isHandToggleEnabled(UUID playerUUID) {
        return handToggle.getOrDefault(playerUUID, false);
    }
}
