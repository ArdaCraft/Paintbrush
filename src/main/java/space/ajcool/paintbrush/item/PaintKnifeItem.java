package space.ajcool.paintbrush.item;

import com.conquestrefabricated.core.block.properties.ModBlockProperties;
import com.conquestrefabricated.core.item.family.Family;
import com.conquestrefabricated.core.item.family.FamilyRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.NonNull;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.config.PaintbrushConfig;

import java.util.List;

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
    public PaintKnifeItem(Properties settings) {
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
    public static LayerChangeResult changeBlockLayer(Player player, BlockPos pos, Direction direction, int delta) {
        if (isControlDown()) {
            pos = pos.relative(direction);
        }

        var world = player.level();
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

        if (!world.isInWorldBounds(resolvedPos)) {
            return LayerChangeResult.outOfBounds(resolvedPos, resolvedState);
        }

        ClientPlayNetworking.send(new Paintbrush.SetBlockPayload(List.of(Pair.of(resolvedPos, NbtUtils.writeBlockState(resolvedState)))));

        player.playSound(SoundEvents.AXE_STRIP, .5F, 1.0F);
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
    private static LayerChange resolveLayerChange(Level world, BlockState state, BlockPos pos, Direction direction, int delta) {
        var layerProp = getLayerProperty(state);

        if (layerProp != null) {
            int value = state.getValue(layerProp);

            if (delta > 0
                    && value >= maxValue(layerProp)
                    && !layerProp.getName().equals("level")) {
                var family = FamilyRegistry.BLOCKS.getFamily(state.getBlock());
                if (shouldPromoteToFullBlock(world, state, pos)
                        && isSwappableLayerMember(state.getBlock())
                        && !family.getMembers().isEmpty()) {
                    return new LayerChange(pos, family.getRoot().defaultBlockState());
                }

                return appendLayerBlock(world, state, family, pos, direction);
            }

            if (delta < 0
                    && value == minValue(layerProp)
                    && !layerProp.getName().equals("level")) {
                if (!PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE) return null;
                var family = FamilyRegistry.BLOCKS.getFamily(state.getBlock());
                if (family.getMembers().isEmpty()
                        || buildLayerState(family, direction, value) == null) {
                    return null;
                }
                return new LayerChange(pos, Blocks.AIR.defaultBlockState());
            }

            var newValue = value + delta;
            if (layerProp.getPossibleValues().contains(newValue)) return new LayerChange(pos, state.setValue(layerProp, newValue));

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
    private static IntegerProperty getLayerProperty(BlockState state) {
        var stateManager = state.getBlock().getStateDefinition();

        var layerProp = asIntProperty(stateManager.getProperty("layer"));
        if (layerProp == null) layerProp = asIntProperty(stateManager.getProperty("layers"));
        if (layerProp == null) layerProp = asIntProperty(stateManager.getProperty("level"));

        return layerProp;
    }

    /**
     * Casts a generic property to an IntegerProperty if possible.
     *
     * @param property the property to cast
     * @return the property as an IntegerProperty, or null if it's not an IntegerProperty
     */
    private static IntegerProperty asIntProperty(Property<?> property) {
        if (property instanceof IntegerProperty intProperty) return intProperty;

        return null;
    }

    /**
     * Casts a generic property to a {@code EnumProperty<Direction>} if it holds Direction values.
     *
     * @param property the property to cast
     * @return the property as an EnumProperty of Direction, or null if it is not one
     */
    private static EnumProperty<Direction> asDirectionProperty(Property<?> property) {
        if (property instanceof EnumProperty<?> enumProperty && enumProperty.getValueClass() == Direction.class) {
            @SuppressWarnings("unchecked")
            var directionProperty = (EnumProperty<Direction>) enumProperty;
            return directionProperty;
        }

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
            var memberId = BuiltInRegistries.BLOCK.getKey(member).toString();
            if (memberId.endsWith("_layer")) {
                var state = setLayerValue(member.defaultBlockState(), value);
                if (state != null) return state;
            }
        }

        for (var member : family.getMembers()) {
            var memberId = BuiltInRegistries.BLOCK.getKey(member).toString();
            if (isPlainSlabId(memberId)) {
                var state = setLayerValue(member.defaultBlockState(), value);
                if (state == null) continue;

                if (state.getBlock().getStateDefinition().getProperty("type") != null) {
                    state = state.setValue(ModBlockProperties.TYPE_UPDOWN, direction == Direction.DOWN ? Half.TOP : Half.BOTTOM);
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
            var memberId = BuiltInRegistries.BLOCK.getKey(member).toString();
            if (!memberId.endsWith("_vertical_slab")) continue;

            var state = member.defaultBlockState();
            var layerProp = asIntProperty(state.getBlock().getStateDefinition().getProperty("layer"));
            if (layerProp == null) continue;

            state = setLayerValue(state, value);
            if (state == null) continue;

            var facingProp = asDirectionProperty(state.getBlock().getStateDefinition().getProperty("facing"));
            if (facingProp != null && facingProp.getPossibleValues().contains(direction)) {
                return state.setValue(facingProp, direction);
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
    private static LayerChange appendLayerBlock(Level world, BlockState sourceState, Family<Block> family, BlockPos pos, Direction direction) {
        if (!PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND || family.getMembers().isEmpty()) return null;
        if (!canAppendFrom(world, sourceState, pos, direction)) return null;

        var targetPos = pos.relative(direction);
        if (!world.getBlockState(targetPos).canBeReplaced()) return null;

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

        if (!layerProp.getPossibleValues().contains(targetValue)) return null;

        return state.setValue(layerProp, targetValue);
    }

    /**
     * Gets the maximum value for an integer property.
     *
     * @param property the integer property to check
     * @return the maximum value this property can hold
     */
    private static int maxValue(IntegerProperty property) {
        return property.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
    }

    /**
     * Gets the minimum value for an integer property.
     *
     * @param property the integer property to check
     * @return the minimum value this property can hold
     */
    private static int minValue(IntegerProperty property) {
        return property.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
    }

    /**
     * Checks if a block is a layer variant that can be swapped to/from its family root.
     *
     * @param block the block to check
     * @return true if the block is a swappable layer variant
     */
    private static boolean isSwappableLayerMember(Block block) {
        var blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

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
    private static boolean shouldPromoteToFullBlock(Level world, BlockState state, BlockPos pos) {
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
    private static boolean canAppendFrom(Level world, BlockState state, BlockPos pos, Direction direction) {
        if (!isVisuallyFullCube(world, state, pos) && !isAtMaxLayer(state)) return false;

        return Block.isFaceFull(state.getShape(world, pos), direction);
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

        return state.getValue(layerProp) >= maxValue(layerProp);
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
    private static boolean isVisuallyFullCube(Level world, BlockState state, BlockPos pos) {
        var shape = state.getShape(world, pos);
        if (shape.isEmpty()) return false;

        return !Shapes.joinIsNotEmpty(Shapes.block(), shape, BooleanOp.ONLY_FIRST);
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

    private static boolean isControlDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL);
    }

    /**
     * Reports a paint knife operation result to the player and logs it if debug output is enabled.
     * Only sends messages and logs if PAINTKNIFE_DEBUG is true.
     *
     * @param player  the player to send the debug message to
     * @param outcome a string describing the outcome (e.g., "SENT", "UNCHANGED")
     * @param result  the layer change result, or null if no change was attempted
     */
    public static void reportDebugResult(Player player, String outcome, LayerChangeResult result) {
        if (!PaintbrushConfig.PAINTKNIFE_DEBUG) {
            return;
        }

        var message = net.minecraft.network.chat.Component.empty()
                .append(net.minecraft.network.chat.Component.literal("Paintbrush: ").withStyle(ChatFormatting.DARK_AQUA))
                .append(net.minecraft.network.chat.Component.literal("Paint knife ").withStyle(ChatFormatting.DARK_GRAY))
                .append(net.minecraft.network.chat.Component.literal(outcome).withStyle(ChatFormatting.AQUA));

        if (result != null && result.pos() != null) {
            message.append(net.minecraft.network.chat.Component.literal(" @ " + result.pos().toShortString()).withStyle(ChatFormatting.GRAY));
        }

        if (result != null && result.state() != null) {
            message.append(net.minecraft.network.chat.Component.literal(" -> " + result.state()).withStyle(ChatFormatting.GRAY));
        }

        player.sendSystemMessage(message);

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
    public @NonNull InteractionResult use(@NonNull Level world, @NonNull Player user, @NonNull InteractionHand hand) {
        return InteractionResult.CONSUME;
    }

    /**
     * Handles secondary use (right-click on block) of the paint knife to increment layer properties.
     * When Ctrl is held, targets the adjacent block instead.
     *
     * @param itemUsageContext the item usage context containing player, position, and world information
     * @return CONSUME if the action was processed, FAIL if cooldown active or invalid target
     */
    @Override
    public @NonNull InteractionResult useOn(UseOnContext itemUsageContext) {
        var world = itemUsageContext.getLevel();
        if (!world.isClientSide()) {
            return InteractionResult.CONSUME;
        }

        var player = itemUsageContext.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (player.getCooldowns().isOnCooldown(itemUsageContext.getItemInHand())) {
            reportDebugResult(player, "cooling down", null);
            return InteractionResult.FAIL;
        }

        if (!world.isInWorldBounds(itemUsageContext.getClickedPos())) {
            reportDebugResult(player, "OUT_OF_BOUNDS", LayerChangeResult.outOfBounds(itemUsageContext.getClickedPos(), null));
            return InteractionResult.FAIL;
        }

        var result = changeBlockLayer(player, itemUsageContext.getClickedPos(), itemUsageContext.getClickedFace(), 1);
        if (result.outcome() == LayerChangeOutcome.SENT) {
            player.getCooldowns().addCooldown(itemUsageContext.getItemInHand(), 4);
        }

        reportDebugResult(player, result.outcome().name(), result);
        return InteractionResult.FAIL;
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
