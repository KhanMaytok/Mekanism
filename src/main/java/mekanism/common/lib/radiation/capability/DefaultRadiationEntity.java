package mekanism.common.lib.radiation.capability;

import mekanism.api.NBTConstants;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.Mekanism;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.CapabilityCache;
import mekanism.common.capabilities.resolver.BasicCapabilityResolver;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.lib.radiation.RadiationManager.RadiationScale;
import mekanism.common.registries.MekanismDamageSource;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

public class DefaultRadiationEntity implements IRadiationEntity {

    private double radiation;

    @Override
    public double getRadiation() {
        return radiation;
    }

    @Override
    public void radiate(double magnitude) {
        radiation += magnitude;
    }

    @Override
    public void update(@NotNull LivingEntity entity) {
        if (entity instanceof Player player && !MekanismUtils.isPlayingMode(player)) {
            return;
        }

        RandomSource rand = entity.level.getRandom();
        double minSeverity = MekanismConfig.general.radiationNegativeEffectsMinSeverity.get();
        double severityScale = RadiationScale.getScaledDoseSeverity(radiation);
        double chance = minSeverity + rand.nextDouble() * (1 - minSeverity);

        if (severityScale > chance) {
            //Calculate effect strength based on radiation severity
            float strength = Math.max(1, (float) Math.log1p(radiation));
            //Hurt randomly
            if (rand.nextBoolean()) {
                entity.hurt(MekanismDamageSource.RADIATION, strength);
                if (entity instanceof ServerPlayer player) {
                    MekanismCriteriaTriggers.RADIATION_DAMAGE.trigger(player);
                }
            }
            if (entity instanceof ServerPlayer player && strength > 0) {
                player.getFoodData().addExhaustion(strength);
            }
        }
    }

    @Override
    public void set(double magnitude) {
        radiation = magnitude;
    }

    @Override
    public void decay() {
        radiation = Math.max(RadiationManager.BASELINE, radiation * MekanismConfig.general.radiationTargetDecayRate.get());
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag ret = new CompoundTag();
        ret.putDouble(NBTConstants.RADIATION, radiation);
        return ret;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        radiation = nbt.getDouble(NBTConstants.RADIATION);
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {

        public static final ResourceLocation NAME = Mekanism.rl(NBTConstants.RADIATION);
        private final IRadiationEntity defaultImpl = new DefaultRadiationEntity();
        private final CapabilityCache capabilityCache = new CapabilityCache();

        public Provider() {
            capabilityCache.addCapabilityResolver(BasicCapabilityResolver.constant(Capabilities.RADIATION_ENTITY, defaultImpl));
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, Direction side) {
            return capabilityCache.getCapability(capability, side);
        }

        public void invalidate() {
            capabilityCache.invalidate(Capabilities.RADIATION_ENTITY, null);
        }

        @Override
        public CompoundTag serializeNBT() {
            return defaultImpl.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            defaultImpl.deserializeNBT(nbt);
        }
    }
}
