package io.github.muslimqol.event;

import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.api.PigPolicy;
import io.github.muslimqol.config.CommonConfig;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Enforces pork drop removal for pigs when configured by PigPolicy.
 */
public class PigDropHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Pig)) {
            return;
        }

        if (CommonConfig.SPEC == null || !CommonConfig.SPEC.isLoaded()) {
            return;
        }

        PigPolicy policy = CommonConfig.PIG_POLICY.get();
        if (policy == PigPolicy.NO_PORK_DROPS || policy == PigPolicy.DISABLED_GAMEPLAY) {
            event.getDrops().removeIf(itemEntity -> {
                ItemStack stack = itemEntity.getItem();
                if (stack.is(Items.PORKCHOP) || stack.is(Items.COOKED_PORKCHOP)) {
                    return true;
                }
                return FoodClassifier.classify(stack).status() == FoodStatus.RESTRICTED;
            });
        }
    }
}
