package mekanism.common.advancements.triggers;

import com.google.gson.JsonObject;
import mekanism.api.JsonConstants;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

public class RadiationDamageTrigger extends SimpleCriterionTrigger<RadiationDamageTrigger.TriggerInstance> {

    private final ResourceLocation id;

    public RadiationDamageTrigger(ResourceLocation id) {
        this.id = id;
    }

    @NotNull
    @Override
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    @Override
    protected TriggerInstance createInstance(@NotNull JsonObject json, @NotNull EntityPredicate.Composite playerPredicate, @NotNull DeserializationContext context) {
        return new TriggerInstance(playerPredicate, GsonHelper.getAsBoolean(json, JsonConstants.KILLED));
    }

    public void trigger(ServerPlayer player) {
        //If it is just any damage regardless of killed or the player is dead
        this.trigger(player, instance -> !instance.killed || player.isDeadOrDying());
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        private final boolean killed;

        public TriggerInstance(EntityPredicate.Composite playerPredicate, boolean killed) {
            super(MekanismCriteriaTriggers.RADIATION_DAMAGE.getId(), playerPredicate);
            this.killed = killed;
        }

        @NotNull
        @Override
        public JsonObject serializeToJson(@NotNull SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.addProperty(JsonConstants.KILLED, killed);
            return json;
        }

        public static RadiationDamageTrigger.TriggerInstance damaged() {
            return new RadiationDamageTrigger.TriggerInstance(EntityPredicate.Composite.ANY, false);
        }

        public static RadiationDamageTrigger.TriggerInstance killed() {
            return new RadiationDamageTrigger.TriggerInstance(EntityPredicate.Composite.ANY, true);
        }
    }
}