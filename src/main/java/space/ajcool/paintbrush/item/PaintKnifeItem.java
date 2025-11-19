package space.ajcool.paintbrush.item;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import space.ajcool.paintbrush.Paintbrush;

import static space.ajcool.paintbrush.Paintbrush.PAINT_KNIFE_ITEM;

public class PaintKnifeItem extends Item
{
    private static boolean thisBoolExistsBecauseIdkWhyItDoublePlaces = false;

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

        if (thisBoolExistsBecauseIdkWhyItDoublePlaces)
        {
            thisBoolExistsBecauseIdkWhyItDoublePlaces = false;
            return ActionResult.FAIL;
        }

        thisBoolExistsBecauseIdkWhyItDoublePlaces = true;

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

        var blockState = player.getWorld().getBlockState(pos);
        var stateManager = blockState.getBlock().getStateManager();

        IntProperty layersProp = (IntProperty) stateManager.getProperty("layer");
        if (layersProp == null) layersProp = (IntProperty) stateManager.getProperty("layers");
        if (layersProp == null) layersProp = (IntProperty) stateManager.getProperty("level");
        if (layersProp == null) return;

        Integer value = blockState.get(layersProp);
        var newValue = value + delta;
        if (!layersProp.getValues().contains(newValue)) return;

        var newState = blockState.with(layersProp, newValue);

        if (newState.equals(blockState)) return;

        var packetBuffer = PacketByteBufs.create();

        packetBuffer.writeInt(1);
        packetBuffer.writeBlockPos(pos);
        packetBuffer.writeNbt(NbtHelper.fromBlockState(newState));

        ClientPlayNetworking.send(Paintbrush.SET_BLOCK_PACKET_ID, packetBuffer);

        Paintbrush.LOGGER.info("Sending packet with value: " + value +  ", New Value: " + newValue + ", Delta: " + delta);

        player.playSound(SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, .5F, 1.0F);

        player.getWorld().setBlockState(pos, newState, 18);
    }
}