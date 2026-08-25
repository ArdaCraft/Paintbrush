package space.ajcool.paintbrush.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.NonNull;
import space.ajcool.paintbrush.Paintbrush;

/**
 * The Tomato entity - a throwable projectile created when the tomato item is used.
 * Similar to snowballs, it creates particles on collision and plays a squish sound.
 */
public class TomatoEntity extends ThrowableItemProjectile {

    /**
     * Creates a TomatoEntity with the given entity type and world.
     *
     * @param entityType the entity type
     * @param world      the world the entity exists in
     */
    public TomatoEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    /**
     * Creates a TomatoEntity thrown by a living entity.
     *
     * @param world the world the entity exists in
     * @param owner the entity that threw the tomato
     */
    public TomatoEntity(Level world, LivingEntity owner) {
        super(Paintbrush.TOMATO, owner, world, Paintbrush.TOMATO_ITEM.getDefaultInstance());
    }

    /**
     * Handles entity status updates (such as collision particle effects).
     * Status 3 triggers the splat particle effect.
     *
     * @param status the status code
     */
    @Override
    @Environment(EnvType.CLIENT)
    public void handleEntityEvent(byte status) {
        if (status != 3) return;
        ParticleOptions particleEffect = this.getParticleParameters();

        var world = this.level();

        for (int i = 0; i < 8; ++i)
            world.addParticle(particleEffect, this.getX(), this.getY(), this.getZ(), world.getRandom().nextGaussian() * 0.05, world.getRandom().nextGaussian() * 0.02, world.getRandom().nextGaussian() * 0.05);
    }

    /**
     * Creates the particle effect for the tomato.
     * Shows the tomato item as particles.
     *
     * @return a particle effect for the tomato item
     */
    @Environment(EnvType.CLIENT)
    private ParticleOptions getParticleParameters() {
        return new ItemParticleOption(ParticleTypes.ITEM, getDefaultItem());
    }

    /**
     * Returns the item type for this thrown entity.
     *
     * @return the tomato item
     */
    @Override
    protected @NonNull Item getDefaultItem() {
        return Paintbrush.TOMATO_ITEM;
    }

    /**
     * Handles collision of the tomato with blocks or entities.
     * Plays a sound, creates particle effects, and removes the entity.
     *
     * @param hitResult the collision hit result
     */
    @Override
    protected void onHit(@NonNull HitResult hitResult) {
        super.onHit(hitResult);

        var world = this.level();

        if (!world.isClientSide()) {
            var hitPos = hitResult.getLocation();

            world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.5f, 1f + (world.getRandom().nextFloat() * 0.2f));

            world.broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }
}
