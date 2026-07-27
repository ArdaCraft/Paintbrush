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
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import space.ajcool.paintbrush.Paintbrush;
import space.ajcool.paintbrush.config.PaintbrushConfig;

import static space.ajcool.paintbrush.Paintbrush.PAINT_KNIFE_ITEM;

public class PaintKnifeItem extends Item
{
    public PaintKnifeItem(Settings settings)
    {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand)
    {
        ItemStack itemStack = user.getStackInHand(hand);
        return TypedActionResult.consume(itemStack);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext itemUsageContext)
    {
        var world = itemUsageContext.getWorld();
        if (!world.isClient()) return ActionResult.CONSUME;

        var player = itemUsageContext.getPlayer();
        if (player == null) return ActionResult.FAIL;

        if (player.getItemCooldownManager().isCoolingDown(PAINT_KNIFE_ITEM)) return ActionResult.FAIL;
        player.getItemCooldownManager().set(PAINT_KNIFE_ITEM, 4);

        var blockPos = itemUsageContext.getBlockPos();
        if (!world.canSetBlock(blockPos)) return ActionResult.FAIL;

        changeBlockLayer(player, blockPos, itemUsageContext.getSide(), 1);

        return ActionResult.CONSUME;
    }

    public static void changeBlockLayer(PlayerEntity player, BlockPos pos, Direction direction, int delta)
    {
        if (Screen.hasControlDown()) pos = pos.offset(direction);

        var world = player.getWorld();
        var blockState = world.getBlockState(pos);
        var change = resolveLayerChange(world, blockState, pos, direction, delta);

        if (change == null || change.state().equals(world.getBlockState(change.pos()))) return;
        if (!world.canSetBlock(change.pos())) return;

        var packetBuffer = PacketByteBufs.create();

        packetBuffer.writeInt(1);
        packetBuffer.writeBlockPos(change.pos());
        packetBuffer.writeNbt(NbtHelper.fromBlockState(change.state()));

        ClientPlayNetworking.send(Paintbrush.SET_BLOCK_PACKET_ID, packetBuffer);

        player.playSound(SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, .5F, 1.0F);

        world.setBlockState(change.pos(), change.state(), 18);
    }

    private static LayerChange resolveLayerChange(World world, BlockState state, BlockPos pos, Direction direction, int delta)
    {
        var layerProp = getLayerProperty(state);

        if (layerProp != null)
        {
            var value = state.get(layerProp);

            if (delta > 0
                    && value >= fullBlockEquivalent(layerProp)
                    && !layerProp.getName().equals("level")
                    && isSwappableLayerMember(state.getBlock()))
            {
                var family = FamilyRegistry.BLOCKS.getFamily(state.getBlock());
                if (!family.getMembers().isEmpty()) return new LayerChange(pos, family.getRoot().getDefaultState());
            }

            if (delta < 0
                    && value == minValue(layerProp)
                    && !layerProp.getName().equals("level"))
            {
                if (!PaintbrushConfig.PAINTKNIFE_ALLOW_DELETE) return null;
                return new LayerChange(pos, Blocks.AIR.getDefaultState());
            }

            var newValue = value + delta;
            if (layerProp.getValues().contains(newValue)) return new LayerChange(pos, state.with(layerProp, newValue));

            return null;
        }

        var family = FamilyRegistry.BLOCKS.getFamily(state.getBlock());
        if (family.getMembers().isEmpty() || !state.getBlock().equals(family.getRoot())) return null;

        if (delta > 0)
        {
            if (!PaintbrushConfig.PAINTKNIFE_ALLOW_APPEND) return null;

            var targetPos = pos.offset(direction);
            if (!world.getBlockState(targetPos).isReplaceable()) return null;

            var layerDirection = direction == Direction.UP || direction == Direction.DOWN
                    ? direction
                    : direction.getOpposite();
            var newState = buildLayerState(family, layerDirection, 1);
            return newState == null ? null : new LayerChange(targetPos, newState);
        }

        if (delta >= 0) return null;

        var newState = buildLayerState(family, direction, -1);
        return newState == null ? null : new LayerChange(pos, newState);
    }

    private record LayerChange(BlockPos pos, BlockState state) { }

    private static BlockState buildLayerState(Family<Block> family, Direction direction, int value)
    {
        if (direction == Direction.UP || direction == Direction.DOWN)
        {
            var horizontalState = buildHorizontalLayerState(family, direction, value);
            if (horizontalState != null) return horizontalState;

            return buildVerticalLayerState(family, direction, value);
        }

        var verticalState = buildVerticalLayerState(family, direction, value);
        if (verticalState != null) return verticalState;

        return buildHorizontalLayerState(family, direction, value);
    }

    private static IntProperty getLayerProperty(BlockState state)
    {
        var stateManager = state.getBlock().getStateManager();

        var layerProp = asIntProperty(stateManager.getProperty("layer"));
        if (layerProp == null) layerProp = asIntProperty(stateManager.getProperty("layers"));
        if (layerProp == null) layerProp = asIntProperty(stateManager.getProperty("level"));

        return layerProp;
    }

    private static IntProperty asIntProperty(Property<?> property)
    {
        if (property instanceof IntProperty intProperty) return intProperty;

        return null;
    }

    private static BlockState buildHorizontalLayerState(Family<Block> family, Direction direction, int value)
    {
        for (var member : family.getMembers())
        {
            var memberId = Registries.BLOCK.getId(member).toString();
            if (memberId.endsWith("_layer"))
            {
                var state = setLayerValue(member.getDefaultState(), value);
                if (state != null) return state;
            }
        }

        for (var member : family.getMembers())
        {
            var memberId = Registries.BLOCK.getId(member).toString();
            if (isPlainSlabId(memberId))
            {
                var state = setLayerValue(member.getDefaultState(), value);
                if (state == null) continue;

                if (state.getBlock().getStateManager().getProperty("type") != null)
                {
                    state = state.with(Slab.TYPE_UPDOWN, direction == Direction.DOWN ? BlockHalf.TOP : BlockHalf.BOTTOM);
                }

                return state;
            }
        }

        return null;
    }

    private static BlockState buildVerticalLayerState(Family<Block> family, Direction direction, int value)
    {
        for (var member : family.getMembers())
        {
            var memberId = Registries.BLOCK.getId(member).toString();
            if (!memberId.endsWith("_vertical_slab")) continue;

            var state = member.getDefaultState();
            var layerProp = asIntProperty(state.getBlock().getStateManager().getProperty("layer"));
            if (layerProp == null) continue;

            state = setLayerValue(state, value);
            if (state == null) continue;

            var facingProp = state.getBlock().getStateManager().getProperty("facing");
            if (facingProp instanceof DirectionProperty directionProperty
                    && directionProperty.getValues().contains(direction))
            {
                return state.with(directionProperty, direction);
            }
        }

        return null;
    }

    private static BlockState setLayerValue(BlockState state, int value)
    {
        var layerProp = getLayerProperty(state);
        if (layerProp == null) return null;

        var targetValue = value < 0
                ? fullBlockEquivalent(layerProp)
                : Math.max(minValue(layerProp), Math.min(value, maxValue(layerProp)));

        if (!layerProp.getValues().contains(targetValue)) return null;

        return state.with(layerProp, targetValue);
    }

    private static int maxValue(IntProperty property)
    {
        return property.getValues().stream().max(Integer::compareTo).orElse(0);
    }

    private static int minValue(IntProperty property)
    {
        return property.getValues().stream().min(Integer::compareTo).orElse(0);
    }

    private static int fullBlockEquivalent(IntProperty property)
    {
        var max = maxValue(property);
        return max == 8 ? max - 1 : max;
    }

    private static boolean isSwappableLayerMember(Block block)
    {
        var blockId = Registries.BLOCK.getId(block).toString();

        return blockId.endsWith("_layer")
                || blockId.endsWith("_vertical_slab")
                || isPlainSlabId(blockId);
    }

    private static boolean isPlainSlabId(String blockId)
    {
        return blockId.endsWith("_slab")
                && !blockId.endsWith("_vertical_slab")
                && !blockId.endsWith("_corner_slab")
                && !blockId.endsWith("_quarter_slab")
                && !blockId.endsWith("_eighth_slab")
                && !blockId.endsWith("_vertical_corner_slab");
    }
}
