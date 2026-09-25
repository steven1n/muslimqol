package io.github.muslimqol.event;

import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Server-authoritative enforcement of food consumption policies (ALLOW, WARN, BLOCK).
 */
public class FoodConsumptionHandler {

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack stack = event.getItem();
        if (!FoodClassifier.isFood(stack)) {
            return;
        }

        FoodClassification classification = FoodClassifier.classify(stack);
        ConsumptionPolicy policy = FoodClassifier.getPolicy(classification.status());

        if (policy == ConsumptionPolicy.BLOCK) {
            event.setCanceled(true);
            if (!player.level().isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("food_policy.muslimqol.block_message", stack.getHoverName()),
                        true
                );
            }
        } else if (policy == ConsumptionPolicy.WARN) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(
                        Component.translatable(
                                "food_policy.muslimqol.warn_message",
                                Component.translatable(classification.status().getTranslationKey()),
                                Component.translatable(classification.getReasonTranslationKey())
                        ),
                        true
                );
            }
        }
    }

    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        ItemStack stack = event.getItem();
        if (!FoodClassifier.isFood(stack)) {
            return;
        }

        FoodClassification classification = FoodClassifier.classify(stack);
        ConsumptionPolicy policy = FoodClassifier.getPolicy(classification.status());

        if (policy == ConsumptionPolicy.BLOCK) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (!FoodClassifier.isFood(stack)) {
            return;
        }

        FoodClassification classification = FoodClassifier.classify(stack);
        ConsumptionPolicy policy = FoodClassifier.getPolicy(classification.status());

        if (policy == ConsumptionPolicy.BLOCK) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            if (!player.level().isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("food_policy.muslimqol.block_message", stack.getHoverName()),
                        true
                );
            }
        }
    }
}
