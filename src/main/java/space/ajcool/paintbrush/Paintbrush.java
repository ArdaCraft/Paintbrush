package space.ajcool.paintbrush;

import com.conquestrefabricated.core.item.family.FamilyRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandException;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtInt;
import net.minecraft.registry.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.ajcool.paintbrush.entity.TomatoEntity;
import space.ajcool.paintbrush.item.PaintKnifeItem;
import space.ajcool.paintbrush.item.PaintbrushItem;
import space.ajcool.paintbrush.item.TomatoItem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Paintbrush implements ModInitializer
{
    public static final String ModID = "paintbrush";
    public static final Logger LOGGER = LoggerFactory.getLogger("paintbrush");

    public static final EntityType<TomatoEntity> TOMATO = FabricEntityTypeBuilder.<TomatoEntity>create(SpawnGroup.MISC, TomatoEntity::new)
            .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
            .trackRangeBlocks(4)
            .trackedUpdateRate(10)
            .build();

    public static final Item PAINTBRUSH_ITEM = new PaintbrushItem(new FabricItemSettings().maxCount(1).fireproof().rarity(Rarity.EPIC));
    public static final Item PAINT_KNIFE_ITEM = new PaintKnifeItem(new FabricItemSettings().maxCount(1).fireproof().rarity(Rarity.EPIC));
    public static final Item TOMATO_ITEM = new TomatoItem(new FabricItemSettings().maxCount(16));

    public static final Identifier SET_BLOCK_PACKET_ID = new Identifier(ModID, "set_block");
    public static final Identifier SET_ITEMSTACK_PACKET_ID = new Identifier(ModID, "set_itemstack");

    public static final RegistryKey<ItemGroup> PAINTBRUSH_ITEM_GROUP_KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), new Identifier(ModID, "item_group"));

    private static final ItemGroup PAINTBRUSH_ITEM_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(PAINTBRUSH_ITEM))
            .displayName(Text.translatable("itemGroup.paintbrush.paintbrush"))
            .build();

    // Store the toggle state per player
    private static final Map<UUID, Boolean> handToggle = new HashMap<>();

    // Check if hand rendering is enabled for the player
    public static boolean isHandToggleEnabled(UUID playerUUID)
    {
        return handToggle.getOrDefault(playerUUID, false);
    }

    // Toggle the hand rendering mode
    public static void toggleHandMode(UUID playerUUID)
    {
        handToggle.put(playerUUID, !isHandToggleEnabled(playerUUID));
    }

    @Override
    public void onInitialize()
    {
        // Registering all the items and groups
        Registry.register(Registries.ENTITY_TYPE, new Identifier(ModID, "tomato"), TOMATO);
        Registry.register(Registries.ITEM, new Identifier(ModID, "paintbrush"), PAINTBRUSH_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ModID, "paint_knife"), PAINT_KNIFE_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ModID, "tomato"), TOMATO_ITEM);

        Registry.register(Registries.ITEM_GROUP, PAINTBRUSH_ITEM_GROUP_KEY, PAINTBRUSH_ITEM_GROUP);

        // Adding items to their respective groups
        ItemGroupEvents.modifyEntriesEvent(PAINTBRUSH_ITEM_GROUP_KEY).register(itemGroup ->
        {
            itemGroup.add(PAINTBRUSH_ITEM.getDefaultStack());
            itemGroup.add(PAINT_KNIFE_ITEM.getDefaultStack());
            var airBrush = PAINTBRUSH_ITEM.getDefaultStack();
            var paintNbt = airBrush.getOrCreateSubNbt("paintbrush");
            paintNbt.put("state", NbtHelper.fromBlockState(Blocks.AIR.getDefaultState()));
            var name = Text.empty().append("Air Paintbrush").formatted(Formatting.RED);
            airBrush.setCustomName(name);
            itemGroup.add(airBrush);
            itemGroup.add(TOMATO_ITEM.getDefaultStack());
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(itemGroup ->
        {
            itemGroup.add(PAINTBRUSH_ITEM);
            itemGroup.add(PAINT_KNIFE_ITEM);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(itemGroup -> itemGroup.add(TOMATO_ITEM));

        // Handle block attacks and brush block interactions
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
        {
            if (world.isClient) return ActionResult.PASS;

            var itemStack = player.getMainHandStack();
            if (!itemStack.isOf(PAINTBRUSH_ITEM)) return ActionResult.PASS;

            return ActionResult.FAIL; // Prevents breaking blocks with paintbrush
        });

        // Networking packets for multiplayer block setting
        ServerPlayNetworking.registerGlobalReceiver(SET_BLOCK_PACKET_ID, (server, player, handler, buf, responseSender) ->
        {
            var blocksInPacket = buf.readInt();

            var brushedBlocks = new HashMap<BlockPos, BlockState>();
            AtomicBoolean canBreak = new AtomicBoolean(true);

            for (int i = 1; i <= blocksInPacket; i++)
            {
                var blockPos = buf.readBlockPos();
                var blockStateNbt = buf.readNbt();

                if (blockStateNbt == null || blockPos == null) continue;

                RegistryWrapper<Block> registryEntryLookup = player.getWorld() != null ? player.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK) : Registries.BLOCK.getReadOnlyWrapper();
                var blockState = NbtHelper.toBlockState(registryEntryLookup, blockStateNbt);

                server.execute(() ->
                {
                    var world = player.getWorld();
                    var blockEntity = world.getBlockEntity(blockPos);

                    if (canBreak.get())
                        canBreak.set(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, blockPos, blockState, blockEntity));

                    brushedBlocks.put(blockPos, blockState);
                });
            }

            if (!canBreak.get())
            {
                var errorMessage = Text.empty()
                        .append(Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                        .append(Text.literal("Unable to alter targeted blocks.").formatted(Formatting.DARK_GRAY));

                player.sendMessage(errorMessage);

                return;
            }

            server.execute(() ->
            {
                LocalSession session = WorldEdit.getInstance().getSessionManager().findByName(player.getName().getString());

                if (session == null) return;

                try (EditSession editSession = session.createEditSession(FabricAdapter.adaptPlayer(player)))
                {
                    for (Map.Entry<BlockPos, BlockState> kvpEntry : brushedBlocks.entrySet())
                    {
                        editSession.setBlock(FabricAdapter.adapt(kvpEntry.getKey()), FabricAdapter.adapt(kvpEntry.getValue()));
                    }

                    session.remember(editSession);
                }
                catch (MaxChangedBlocksException e)
                {
                    throw new RuntimeException(e);
                }
            });
        });

        // Networking for item stack changes
        ServerPlayNetworking.registerGlobalReceiver(SET_ITEMSTACK_PACKET_ID, (server, player, handler, buf, responseSender) ->
        {
            var slot = buf.readInt();
            var itemstack = buf.readItemStack();

            if (itemstack == null) return;

            server.execute(() ->
            {
                player.getInventory().setStack(slot, itemstack);
            });
        });

        // Registering commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        {
            configureCommand("paintbrush", dispatcher);
            configureCommand("pb", dispatcher);
        });
    }

    private void configureCommand(String commandName, CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(CommandManager.literal(commandName)
                .executes(this::PaintBrushCommand)
                .then(CommandManager.literal("hand")
                        .executes(context -> handleHandToggleCommand(context)))
                .then(CommandManager.literal("size")
                        .then(CommandManager.argument("value", IntegerArgumentType.integer())
                                .executes(this::setBrushSize)))
        );
    }

    private int handleHandToggleCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayer();

        if (player != null)
        {
            UUID playerUUID = player.getUuid();
            toggleHandMode(playerUUID);

            boolean isHandEnabled = isHandToggleEnabled(playerUUID);
            String message = isHandEnabled
                    ? "Paintbrush: Now showing the material in hand."
                    : "Paintbrush: Now only showing the material in the hotbar.";

            player.sendMessage(Text.literal(message).formatted(Formatting.AQUA));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int PaintBrushCommand(CommandContext<ServerCommandSource> context)
    {
        var player = context.getSource().getPlayer();

        if (player != null)
        {
            player.getInventory().insertStack(PAINTBRUSH_ITEM.getDefaultStack());

            var message = Text.empty()
                    .append(Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                    .append(Text.literal("Added a dry paintbrush to inventory!").formatted(Formatting.DARK_GRAY));

            player.sendMessage(message);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int setBrushSize(CommandContext<ServerCommandSource> context) throws CommandSyntaxException
    {
        var player = context.getSource().getPlayer();

        if (player != null)
        {
            int size = IntegerArgumentType.getInteger(context, "value");

            if (size > 5 || size < 1)
            {
                var message = Text.empty()
                        .append(Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                        .append(Text.literal("selected size must be between 1 and 5.").formatted(Formatting.RED));

                player.sendMessage(message);

                return Command.SINGLE_SUCCESS;
            }

            var itemStack = player.getInventory().getMainHandStack();

            if (!itemStack.isOf(PAINTBRUSH_ITEM))
            {
                player.sendMessage(Text.empty()
                        .append(Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                        .append(Text.literal("You must have a paintbrush in your main hand to set the brush size.").formatted(Formatting.RED)));

                return Command.SINGLE_SUCCESS;
            }

            var paintNbt = itemStack.getOrCreateSubNbt("paintbrush");
            paintNbt.put("size", NbtInt.of(size));

            var iHaveAState = false;
            BlockState blockState;

            if (paintNbt.contains("state"))
            {
                var state = paintNbt.getCompound("state");
                RegistryWrapper<Block> registryEntryLookup = player.getWorld() != null ? player.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK) : Registries.BLOCK.getReadOnlyWrapper();
                blockState = NbtHelper.toBlockState(registryEntryLookup, state);
                iHaveAState = true;
            }
            else
            {
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

            context.getSource().getServer().execute(() -> player.getInventory().setStack(player.getInventory().selectedSlot, itemStack));

            var message = Text.empty()
                    .append(Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                    .append(Text.literal("size set to").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(" " + size).formatted(Formatting.AQUA))
                    .append(Text.literal(".").formatted(Formatting.DARK_GRAY));

            player.sendMessage(message);
        }

        return Command.SINGLE_SUCCESS;
    }
}
