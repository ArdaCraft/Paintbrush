package space.ajcool.paintbrush.item;

import com.conquestrefabricated.content.blocks.block.Slab;
import com.conquestrefabricated.core.item.family.Family;
import com.conquestrefabricated.core.item.family.FamilyRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.config.PaintbrushConfig;

import static space.ajcool.paintbrush.Paintbrush.PAINT_KNIFE_ITEM;

/**
 * The Paint Knife item implementation.
 * Allows players to increment or decrement layer-like block properties (layer, layers, level).
 * Can swap full blocks for their layered variants and vice versa.
 * Supports both horizontal and vertical layer blocks with directional properties.
 */
public class PaintKnifeItem extends Item {
    /**
     * Creates a new PaintKnifeItem with the given settings.
     *
     * @param settings the item settings
     */
    public PaintKnifeItem(Settings settings) {
        super(settings);
    }

    /**
     * Changes the layer property of a block at the given position.
     * Supports incrementing/decrementing layer values, swapping to family roots, appending layers, or deleting blocks.
     * When Ctrl is held, targets the adjacent block in the given direction.
     *
     * @param player    the player performing the action
     * @param pos       the block position to modify
     * @param direction the direction clicked, used for appending/deleting and Ctrl-offset
     * @param delta     the change amount: 1 to increment, -1 to decrement, 0 for special operations
     */
    public static LayerChangeResult changeBlockLayer(PlayerEntity player, BlockPos pos, Direction direction, int delta) {
        if (Screen.hasControlDown()) {
            pos = pos.offset(direction);
        }

        var world = player.getWorld();
        var blockState = world.getBlockState(pos);
        var change = resolveLayerChange(world, blockState, pos, direction, delta);

        if (change == null) {
            return LayerChangeResult.noTarget(pos, null);
        }

        var resolvedState = change.state();
        var resolvedPos = change.pos();

        if (resolvedState.equals(world.getBlockState(resolvedPos))) {
            return LayerChangeResult.unchanged(resolvedPos, resolvedState);
        }

        if (!world.canSetBlock(resolvedPos)) {
            return LayerChangeResult.outOfBounds(resolvedPos, resolvedState);
        }

        var packetBuffer = PacketByteBufs.create();

        packetBuffer.writeInt(1);
        packetBuffer.writeBlockPos(resolvedPos);
        packetBuffer.writeNbt(NbtHelper.fromBlockState(resolvedState));

        ClientPlayNetworking.send(Paintbrush.SET_BLOCK_PACKET_ID, packetBuffer);

        player.playSound(SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, .5F, 1.0F);
        return LayerChangeResult.sent(resolvedPos, resolvedState);
    }

    /**
     * Resolves what layer change should be applied to the block at the given position.
     * Handles conversion between layered and full blocks, and respects configuration settings.
     *
     * @param world     the world containing the block
     * @param state     the current block state
     * @param pos       the block position
     * @param direction the direction of the click
     * @param delta     the requested change amount
     * @return a LayerChange containing the new position and state, or null if no change is possible
     */
    private static LayerChange resolveLayerChange(World world, BlockState state, BlockPos pos, Direction direction, int delta) {
        var layerProp = getLayerProperty(state);

        if (layerProp != null) {
            var value = state.get(layerProp);

            if (delta > 0
                    && value >= maxValue(layerProp)
                    && !layerProp.getName().equals("level")) {
                var family = FamilyRegistry.BLOCKS.getFamily(state.getBlock());
                if (shouldPromoteToFullBlock(world, state, pos)
                        && isSwappableLayerMember(state.getBlock())
                        && !family.getMembers().isEmpty()) {
                    return new LayerChange(pos, family.getRoot().getDefaultState());
                }

                return appendLayerBlock(world, state, family, pos, direction);
            }

            if (delta < 0
                    && value == minValue(layerProp)
                    && !layerProp.getName().equals("level")) {
                if (!PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE) return null;
                return new LayerChange(pos, Blocks.AIR.getDefaultState());
            }

            var newValue = value + delta;
            if (layerProp.getValues().contains(newValue)) return new LayerChange(pos, state.with(layerProp, newValue));

            return null;
        }

        var family = FamilyRegistry.BLOCKS.getFamily(state.getBlock());
        if (family.getMembers().isEmpty() || !state.getBlock().equals(family.getRoot())) return null;

        if (delta > 0) {
            return appendLayerBlock(world, state, family, pos, direction);
        }

        if (delta == 0) return null;

        var newState = buildLayerState(family, direction, -1);
        return newState == null ? null : new LayerChange(pos, newState);
    }

    /**
     * Builds a layered block state from a block family, preferring vertical or horizontal blocks based on direction.
     *
     * @param family    the block family to select a layer block from
     * @param direction the direction that was clicked
     * @param value     the layer value to set
     * @return a layered block state with the given value, or null if no suitable layer block exists
     */
    private static BlockState buildLayerState(Family<Block> family, Direction direction, int value) {
        if (direction == Direction.UP || direction == Direction.DOWN) {
            var horizontalState = buildHorizontalLayerState(family, direction, value);
            if (horizontalState != null) return horizontalState;

            return buildVerticalLayerState(family, direction, value);
        }

        var verticalState = buildVerticalLayerState(family, direction, value);
        if (verticalState != null) return verticalState;

        return buildHorizontalLayerState(family, direction, value);
    }

    /**
     * Gets the layer-like property from a block state.
     * Checks for "layer", "layers", or "level" properties in order of preference.
     *
     * @param state the block state to check
     * @return the layer property, or null if the block has no layer-like properties
     */
    private static IntProperty getLayerProperty(BlockState state) {
        var stateManager = state.getBlock().getStateManager();

        var layerProp = asIntProperty(stateManager.getProperty("layer"));
        if (layerProp == null) layerProp = asIntProperty(stateManager.getProperty("layers"));
        if (layerProp == null) layerProp = asIntProperty(stateManager.getProperty("level"));

        return layerProp;
    }

    /**
     * Casts a generic property to an IntProperty if possible.
     *
     * @param property the property to cast
     * @return the property as an IntProperty, or null if it's not an IntProperty
     */
    private static IntProperty asIntProperty(Property<?> property) {
        if (property instanceof IntProperty intProperty) return intProperty;

        return null;
    }

    /**
     * Builds a horizontal layer block state (standard or double slab) from a family.
     * Prefers layer blocks, then falls back to regular slabs with appropriate type property.
     *
     * @param family    the block family to select from
     * @param direction the direction clicked (UP/DOWN for slab type)
     * @param value     the layer value to set
     * @return a horizontal layer block state, or null if none exists in the family
     */
    private static BlockState buildHorizontalLayerState(Family<Block> family, Direction direction, int value) {
        for (var member : family.getMembers()) {
            var memberId = Registries.BLOCK.getId(member).toString();
            if (memberId.endsWith("_layer")) {
                var state = setLayerValue(member.getDefaultState(), value);
                if (state != null) return state;
            }
        }

        for (var member : family.getMembers()) {
            var memberId = Registries.BLOCK.getId(member).toString();
            if (isPlainSlabId(memberId)) {
                var state = setLayerValue(member.getDefaultState(), value);
                if (state == null) continue;

                if (state.getBlock().getStateManager().getProperty("type") != null) {
                    state = state.with(Slab.TYPE_UPDOWN, direction == Direction.DOWN ? BlockHalf.TOP : BlockHalf.BOTTOM);
                }

                return state;
            }
        }

        return null;
    }

    /**
     * Builds a vertical layer block state from a family.
     * Conquest vertical slabs anchor on the edge opposite {@code direction} and grow toward it.
     *
     * @param family    the block family to select from
     * @param direction the direction to face (used for vertical slab facing property)
     * @param value     the layer value to set
     * @return a vertical layer block state with the given direction, or null if none exists
     */
    private static BlockState buildVerticalLayerState(Family<Block> family, Direction direction, int value) {
        for (var member : family.getMembers()) {
            var memberId = Registries.BLOCK.getId(member).toString();
            if (!memberId.endsWith("_vertical_slab")) continue;

            var state = member.getDefaultState();
            var layerProp = asIntProperty(state.getBlock().getStateManager().getProperty("layer"));
            if (layerProp == null) continue;

            state = setLayerValue(state, value);
            if (state == null) continue;

            var facingProp = state.getBlock().getStateManager().getProperty("facing");
            if (facingProp instanceof DirectionProperty directionProperty
                    && directionProperty.getValues().contains(direction)) {
                return state.with(directionProperty, direction);
            }
        }

        return null;
    }

    /**
     * Appends a new layer block adjacent to the clicked face when the family supports it and
     * the source block visually fills the clicked face.
     * For Conquest vertical slabs, {@code facing=direction} keeps the new slab flush with the clicked face and
     * growing outward from the source block.
     *
     * @param world       the world containing the blocks
     * @param sourceState the clicked block state
     * @param family      the block family to select from
     * @param pos         the clicked block position
     * @param direction   the direction that was clicked
     * @return a layer change for the appended block, or null if appending is not allowed
     */
    private static LayerChange appendLayerBlock(World world, BlockState sourceState, Family<Block> family, BlockPos pos, Direction direction) {
        if (!PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND || family.getMembers().isEmpty()) return null;
        if (!canAppendFrom(world, sourceState, pos, direction)) return null;

        var targetPos = pos.offset(direction);
        if (!world.getBlockState(targetPos).isReplaceable()) return null;

        var newState = buildLayerState(family, direction, 1);
        return newState == null ? null : new LayerChange(targetPos, newState);
    }

    /**
     * Sets the layer value on a block state, clamping to the property's valid range.
     *
     * @param state the block state to modify
     * @param value the layer value to set, negative values default to max
     * @return the modified block state, or null if the state has no layer property
     */
    private static BlockState setLayerValue(BlockState state, int value) {
        var layerProp = getLayerProperty(state);
        if (layerProp == null) return null;

        var targetValue = value < 0
                ? maxValue(layerProp)
                : Math.max(minValue(layerProp), Math.min(value, maxValue(layerProp)));

        if (!layerProp.getValues().contains(targetValue)) return null;

        return state.with(layerProp, targetValue);
    }

    /**
     * Gets the maximum value for an integer property.
     *
     * @param property the integer property to check
     * @return the maximum value this property can hold
     */
    private static int maxValue(IntProperty property) {
        return property.getValues().stream().max(Integer::compareTo).orElse(0);
    }

    /**
     * Gets the minimum value for an integer property.
     *
     * @param property the integer property to check
     * @return the minimum value this property can hold
     */
    private static int minValue(IntProperty property) {
        return property.getValues().stream().min(Integer::compareTo).orElse(0);
    }

    /**
     * Checks if a block is a layer variant that can be swapped to/from its family root.
     *
     * @param block the block to check
     * @return true if the block is a swappable layer variant
     */
    private static boolean isSwappableLayerMember(Block block) {
        var blockId = Registries.BLOCK.getId(block).toString();

        return blockId.endsWith("_layer")
                || blockId.endsWith("_vertical_slab")
                || isPlainSlabId(blockId);
    }

    /**
     * Determines whether a max-layer block should be promoted to its family's full block.
     * Respects the PAINTKNIFE_FULL_BLOCKS configuration:
     * - ALL: always promote
     * - NONE: never promote
     * - PARTIAL: promote only if the block doesn't visually fill a full cube
     *
     * @param world the world containing the block
     * @param state the current block state
     * @param pos   the block position
     * @return true if the block should be promoted to its family root
     */
    private static boolean shouldPromoteToFullBlock(World world, BlockState state, BlockPos pos) {
        return switch (PaintbrushConfig.PAINTKNIFE_FULL_BLOCKS) {
            case ALL -> true;
            case NONE -> false;
            case PARTIAL -> !isVisuallyFullCube(world, state, pos);
        };
    }

    /**
     * Checks whether the clicked source block can visually support an appended layer block.
     *
     * @param world     the world containing the block
     * @param state     the clicked block state
     * @param pos       the clicked block position
     * @param direction the clicked face
     * @return true if the source fills the clicked face and is full or already at max layer
     */
    private static boolean canAppendFrom(World world, BlockState state, BlockPos pos, Direction direction) {
        if (!isVisuallyFullCube(world, state, pos) && !isAtMaxLayer(state)) return false;

        return Block.isFaceFullSquare(state.getOutlineShape(world, pos), direction);
    }

    /**
     * Checks if a block state has a maxed layer-like property that can append.
     *
     * @param state the block state to check
     * @return true if the block has a non-level layer property at its maximum value
     */
    private static boolean isAtMaxLayer(BlockState state) {
        var layerProp = getLayerProperty(state);
        if (layerProp == null || layerProp.getName().equals("level")) return false;

        return state.get(layerProp) >= maxValue(layerProp);
    }

    /**
     * Checks if a block state visually fills an entire cube in the world.
     * Used to determine if promoting a max-layer block to full would be a no-op.
     *
     * @param world the world containing the block
     * @param state the block state to check
     * @param pos   the block position
     * @return true if the block's outline shape is not a full cube
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isVisuallyFullCube(World world, BlockState state, BlockPos pos) {
        var shape = state.getOutlineShape(world, pos);
        if (shape.isEmpty()) return false;

        return !VoxelShapes.matchesAnywhere(VoxelShapes.fullCube(), shape, BooleanBiFunction.ONLY_FIRST);
    }

    /**
     * Checks if a block ID represents a plain horizontal slab (not vertical, corner, or eighth variants).
     *
     * @param blockId the block ID to check
     * @return true if the block is a plain slab
     */
    private static boolean isPlainSlabId(String blockId) {
        return blockId.endsWith("_slab")
                && !blockId.endsWith("_vertical_slab")
                && !blockId.endsWith("_corner_slab")
                && !blockId.endsWith("_quarter_slab")
                && !blockId.endsWith("_eighth_slab")
                && !blockId.endsWith("_vertical_corner_slab");
    }

    /**
     * Reports a paint knife operation result to the player and logs it if debug output is enabled.
     * Only sends messages and logs if PAINTKNIFE_DEBUG is true.
     *
     * @param player  the player to send the debug message to
     * @param outcome a string describing the outcome (e.g., "SENT", "UNCHANGED")
     * @param result  the layer change result, or null if no change was attempted
     */
    public static void reportDebugResult(PlayerEntity player, String outcome, LayerChangeResult result) {
        if (!PaintbrushConfig.PAINTKNIFE_DEBUG) {
            return;
        }

        var message = net.minecraft.text.Text.empty()
                .append(net.minecraft.text.Text.literal("Paintbrush: ").formatted(Formatting.DARK_AQUA))
                .append(net.minecraft.text.Text.literal("Paint knife ").formatted(Formatting.DARK_GRAY))
                .append(net.minecraft.text.Text.literal(outcome).formatted(Formatting.AQUA));

        if (result != null && result.pos() != null) {
            message.append(net.minecraft.text.Text.literal(" @ " + result.pos().toShortString()).formatted(Formatting.GRAY));
        }

        if (result != null && result.state() != null) {
            message.append(net.minecraft.text.Text.literal(" -> " + result.state()).formatted(Formatting.GRAY));
        }

        player.sendMessage(message);

        if (result == null) {
            Paintbrush.LOGGER.info("Paintbrush - Paint knife result={} player={}", outcome, player.getName().getString());
            return;
        }

        Paintbrush.LOGGER.info("Paintbrush - Paint knife result={} player={} pos={} state={}",
                outcome, player.getName().getString(), result.pos(), result.state());
    }

    /**
     * Handles the primary use (left-click in air) of the paint knife.
     *
     * @param world the world where the action occurs
     * @param user  the player using the item
     * @param hand  the hand the item is in
     * @return a typed action result indicating the action was consumed
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        return TypedActionResult.consume(itemStack);
    }

    /**
     * Handles secondary use (right-click on block) of the paint knife to increment layer properties.
     * When Ctrl is held, targets the adjacent block instead.
     *
     * @param itemUsageContext the item usage context containing player, position, and world information
     * @return CONSUME if the action was processed, FAIL if cooldown active or invalid target
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext itemUsageContext) {
        var world = itemUsageContext.getWorld();
        if (!world.isClient()) {
            return ActionResult.CONSUME;
        }

        var player = itemUsageContext.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }

        if (player.getItemCooldownManager().isCoolingDown(PAINT_KNIFE_ITEM)) {
            reportDebugResult(player, "cooling down", null);
            return ActionResult.FAIL;
        }

        if (!world.canSetBlock(itemUsageContext.getBlockPos())) {
            reportDebugResult(player, "OUT_OF_BOUNDS", LayerChangeResult.outOfBounds(itemUsageContext.getBlockPos(), null));
            return ActionResult.FAIL;
        }

        var result = changeBlockLayer(player, itemUsageContext.getBlockPos(), itemUsageContext.getSide(), 1);
        if (result.outcome() == LayerChangeOutcome.SENT) {
            player.getItemCooldownManager().set(PAINT_KNIFE_ITEM, 4);
        }

        reportDebugResult(player, result.outcome().name(), result);
        return ActionResult.FAIL;
    }

    /**
     * Enumeration of possible outcomes when attempting to change a block's layer property.
     */
    public enum LayerChangeOutcome {
        /** The layer change was successfully sent to the server. */
        SENT,
        /** No target block was found or the block has no layer property. */
        NO_TARGET,
        /** The target block state is already set to the desired value. */
        UNCHANGED,
        /** The target position is outside the world's build limits or is protected. */
        OUT_OF_BOUNDS
    }

    /**
     * Represents a proposed change to a block's layer state.
     *
     * @param pos   the position of the block to change
     * @param state the new block state to apply
     */
    private record LayerChange(BlockPos pos, BlockState state) {
    }

    /**
     * Represents the result of a paint knife layer change operation.
     *
     * @param outcome the result outcome
     * @param pos     the block position affected (or target position for failed attempts)
     * @param state   the block state that was/would be applied
     */
    public record LayerChangeResult(LayerChangeOutcome outcome, BlockPos pos, BlockState state) {
        private static LayerChangeResult sent(BlockPos pos, BlockState state) {
            return new LayerChangeResult(LayerChangeOutcome.SENT, pos, state);
        }

        @SuppressWarnings("SameParameterValue")
        private static LayerChangeResult noTarget(BlockPos pos, BlockState state) {
            return new LayerChangeResult(LayerChangeOutcome.NO_TARGET, pos, state);
        }

        private static LayerChangeResult unchanged(BlockPos pos, BlockState state) {
            return new LayerChangeResult(LayerChangeOutcome.UNCHANGED, pos, state);
        }

        private static LayerChangeResult outOfBounds(BlockPos pos, BlockState state) {
            return new LayerChangeResult(LayerChangeOutcome.OUT_OF_BOUNDS, pos, state);
        }
    }
}
