package io.github.muslimqol.event;

import io.github.muslimqol.api.PigPolicy;
import io.github.muslimqol.config.CommonConfig;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Handles pig spawning policy enforcement without removing entity or registry registrations.
 */
public class PigSpawnHandler {

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Pig)) {
            return;
        }

        if (CommonConfig.SPEC == null || !CommonConfig.SPEC.isLoaded()) {
            return;
        }

        PigPolicy policy = CommonConfig.PIG_POLICY.get();
        if (policy == PigPolicy.NO_NATURAL_SPAWN || policy == PigPolicy.DISABLED_GAMEPLAY) {
            MobSpawnType type = event.getSpawnType();
            if (type == MobSpawnType.NATURAL
                    || type == MobSpawnType.CHUNK_GENERATION
                    || type == MobSpawnType.STRUCTURE) {
                event.setSpawnCancelled(true);
                event.setCanceled(true);
            }
        }
    }
}
