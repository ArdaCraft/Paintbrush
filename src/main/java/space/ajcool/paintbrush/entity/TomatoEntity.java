package space.ajcool.paintbrush.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import space.ajcool.paintbrush.Paintbrush;

/**
 * The Tomato entity - a throwable projectile created when the tomato item is used.
 * Similar to snowballs, it creates particles on collision and plays a squish sound.
 */
public class TomatoEntity extends ThrownItemEntity {

    /**
     * Creates a TomatoEntity with the given entity type and world.
     *
     * @param entityType the entity type
     * @param world      the world the entity exists in
     */
    public TomatoEntity(EntityType<? extends ThrownItemEntity> entityType, World world) {
        super(entityType, world);
    }

    /**
     * Creates a TomatoEntity thrown by a living entity.
     *
     * @param world the world the entity exists in
     * @param owner the entity that threw the tomato
     */
    public TomatoEntity(World world, LivingEntity owner) {
        super(Paintbrush.TOMATO, owner, world);
    }

    /**
     * Creates a TomatoEntity at the specified coordinates.
     *
     * @param world the world the entity exists in
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param z     the z coordinate
     */
    @SuppressWarnings("unused")
    public TomatoEntity(World world, double x, double y, double z) {
        super(Paintbrush.TOMATO, x, y, z, world);
    }

    /**
     * Handles entity status updates (such as collision particle effects).
     * Status 3 triggers the splat particle effect.
     *
     * @param status the status code
     */
    @Environment(EnvType.CLIENT)
    public void handleStatus(byte status) {
        if (status != 3) return;
        ParticleEffect particleEffect = this.getParticleParameters();

        var world = this.getWorld();

        for (int i = 0; i < 8; ++i)
            world.addParticle(particleEffect, this.getX(), this.getY(), this.getZ(), world.random.nextGaussian() * 0.05, world.random.nextGaussian() * 0.02, world.random.nextGaussian() * 0.05);
    }

    /**
     * Creates the particle effect for the tomato.
     * Shows the tomato item as particles.
     *
     * @return a particle effect for the tomato item
     */
    @Environment(EnvType.CLIENT)
    private ParticleEffect getParticleParameters() {
        return new ItemStackParticleEffect(ParticleTypes.ITEM, getDefaultItem().getDefaultStack());
    }

    /**
     * Returns the item type for this thrown entity.
     *
     * @return the tomato item
     */
    @Override
    protected Item getDefaultItem() {
        return Paintbrush.TOMATO_ITEM;
    }

    /**
     * Handles collision of the tomato with blocks or entities.
     * Plays a sound, creates particle effects, and removes the entity.
     *
     * @param hitResult the collision hit result
     */
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);

        var world = this.getWorld();

        if (!world.isClient) {
            var hitPos = hitResult.getPos();

            world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.NEUTRAL, 0.5f, 1f + (world.getRandom().nextFloat() * 0.2f));

            world.sendEntityStatus(this, (byte) 3);
            this.discard();
        }
    }
}
