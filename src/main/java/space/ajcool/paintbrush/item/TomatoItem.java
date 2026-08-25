package space.ajcool.paintbrush.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
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
    public TomatoItem(Properties settings) {
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
    public @NonNull InteractionResult use(Level world, Player user, @NonNull InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5f, 0.4f / (world.getRandom().nextFloat() * 0.4f + 0.8f));

        //user.getCooldowns().set(this, 5);

        if (world instanceof ServerLevel serverLevel) {
            TomatoEntity snowballEntity = new TomatoEntity(world, user);
            snowballEntity.setItem(itemStack);
            snowballEntity.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.5F, 0F);
            serverLevel.addFreshEntity(snowballEntity);
        }

        user.awardStat(Stats.ITEM_USED.get(this));

        if (!user.getAbilities().instabuild) {
            itemStack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }
}
