package io.github.muslimqol.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.food.FoodClassifier;
import io.github.muslimqol.util.ResourceLocationUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * Handles client-side item slot overlay rendering for food status icons.
 */
public final class FoodOverlayRenderer {

    private FoodOverlayRenderer() {}

    public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        IItemDecorator decorator = new FoodStatusItemDecorator();
        for (Item item : BuiltInRegistries.ITEM) {
            event.register(item, decorator);
        }
    }

    public static class FoodStatusItemDecorator implements IItemDecorator {
        private static final ResourceLocation HALAL_ICON = ResourceLocationUtil.modLoc("textures/gui/food_status/halal.png");
        private static final ResourceLocation RESTRICTED_ICON = ResourceLocationUtil.modLoc("textures/gui/food_status/restricted.png");
        private static final ResourceLocation DOUBTFUL_ICON = ResourceLocationUtil.modLoc("textures/gui/food_status/doubtful.png");
        private static final ResourceLocation UNKNOWN_ICON = ResourceLocationUtil.modLoc("textures/gui/food_status/unknown.png");

        @Override
        public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
            if (ClientConfig.SPEC == null || !ClientConfig.SPEC.isLoaded() || !ClientConfig.SHOW_INVENTORY_ICONS.get()) {
                return false;
            }

            if (!FoodClassifier.isFood(stack)) {
                return false;
            }

            FoodClassification classification = FoodClassifier.classify(stack);
            FoodStatus status = classification.status();

            boolean shouldRender = switch (status) {
                case HALAL -> ClientConfig.SHOW_HALAL_ICON.get();
                case RESTRICTED -> ClientConfig.SHOW_RESTRICTED_ICON.get();
                case DOUBTFUL -> ClientConfig.SHOW_DOUBTFUL_ICON.get();
                case UNKNOWN -> ClientConfig.SHOW_UNKNOWN_ICON.get();
            };

            if (!shouldRender) {
                return false;
            }

            ResourceLocation texture = switch (status) {
                case HALAL -> HALAL_ICON;
                case RESTRICTED -> RESTRICTED_ICON;
                case DOUBTFUL -> DOUBTFUL_ICON;
                case UNKNOWN -> UNKNOWN_ICON;
            };

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.blit(texture, xOffset + 8, yOffset, 8, 8, 0.0f, 0.0f, 16, 16, 16, 16);
            RenderSystem.disableBlend();
            return true;
        }
    }
}
