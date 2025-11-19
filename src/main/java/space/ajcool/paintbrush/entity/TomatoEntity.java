package space.ajcool.paintbrush.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import space.ajcool.paintbrush.Paintbrush;

public class TomatoEntity extends ThrownItemEntity
{
    public TomatoEntity(EntityType<? extends ThrownItemEntity> entityType, World world)
    {
        super(entityType, world);
    }

    public TomatoEntity(World world, LivingEntity owner) {
        super(Paintbrush.TOMATO, owner, world);
    }

    public TomatoEntity(World world, double x, double y, double z) {
        super(Paintbrush.TOMATO, x, y, z, world);
    }

    @Override
    protected Item getDefaultItem()
    {
        return Paintbrush.TOMATO_ITEM;
    }

    @Environment(EnvType.CLIENT)
    private ParticleEffect getParticleParameters() {
        return new ItemStackParticleEffect(ParticleTypes.ITEM, getDefaultItem().getDefaultStack());
    }

    @Environment(EnvType.CLIENT)
    public void handleStatus(byte status) {
        if (status != 3) return;
        ParticleEffect particleEffect = this.getParticleParameters();

        var world = this.getWorld();

        for(int i = 0; i < 8; ++i)
            world.addParticle(particleEffect, this.getX(), this.getY(), this.getZ(), world.random.nextGaussian() * 0.05, world.random.nextGaussian() * 0.02, world.random.nextGaussian() * 0.05);
    }

    protected void onCollision(HitResult hitResult)
    {
        super.onCollision(hitResult);

        var world = this.getWorld();

        if (!world.isClient)
        {
            var hitPos = hitResult.getPos();

            world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.NEUTRAL, 0.5f, 1f + (world.getRandom().nextFloat() * 0.2f));

            world.sendEntityStatus(this, (byte) 3);
            this.discard();
        }
    }
}
