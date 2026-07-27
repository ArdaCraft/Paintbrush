package space.ajcool.paintbrush.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import space.ajcool.paintbrush.entity.TomatoEntity;

/**
 * The Tomato item - a throwable food item similar to snowballs.
 * When used, creates a TomatoEntity projectile and throws it.
 */
public class TomatoItem extends Item {

    /**
     * Creates a new TomatoItem with the given settings.
     *
     * @param settings the item settings
     */
    public TomatoItem(Settings settings) {
        super(settings);
    }

    /**
     * Handles throwing the tomato when the player uses the item.
     * Creates a TomatoEntity on the server and sends it flying.
     *
     * @param world the world where the action occurs
     * @param user  the player using the item
     * @param hand  the hand the item is in
     * @return a typed action result indicating success
     */
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5f, 0.4f / (world.getRandom().nextFloat() * 0.4f + 0.8f));

        //user.getItemCooldownManager().set(this, 5);

        if (!world.isClient) {
            TomatoEntity snowballEntity = new TomatoEntity(world, user);
            snowballEntity.setItem(itemStack);
            snowballEntity.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.5F, 0F);
            world.spawnEntity(snowballEntity);
        }

        user.incrementStat(Stats.USED.getOrCreateStat(this));

        if (!user.getAbilities().creativeMode) {
            itemStack.decrement(1);
        }

        return TypedActionResult.success(itemStack, world.isClient());
    }
}
