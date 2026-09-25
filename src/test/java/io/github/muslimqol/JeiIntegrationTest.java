package io.github.muslimqol;

import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.client.FoodClassificationTooltipFormatter;
import io.github.muslimqol.client.compat.jei.MuslimQolJeiPlugin;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.food.FoodClassificationRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiIntegrationTest {

    private final ResourceLocation appleId = ResourceLocation.parse("minecraft:apple");
    private final ResourceLocation porkId = ResourceLocation.parse("minecraft:porkchop");
    private final ResourceLocation stoneId = ResourceLocation.parse("minecraft:stone");

    @BeforeAll
    static void init() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @BeforeEach
    @AfterEach
    void resetState() {
        FoodClassificationRegistry.clearAll();
        FoodCompatibilityManager.resetToDefaults();
    }

    @Test
    void testTooltipFormatterMapsHalalCorrectly() {
        assertTrue(FoodClassificationTooltipFormatter.shouldShowTooltip(appleId));

        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(appleId);
        assertFalse(lines.isEmpty(), "Halal food must produce tooltip lines");

        // First content line after separator is status
        Component statusLine = lines.get(1);
        assertEquals(ChatFormatting.GREEN, FoodClassificationTooltipFormatter.getStatusColor(FoodStatus.HALAL));
        assertTrue(statusLine.getContents() instanceof TranslatableContents);
        assertEquals("food_status.muslimqol.halal", ((TranslatableContents) statusLine.getContents()).getKey());

        // Reason line
        Component reasonLine = lines.get(2);
        assertTrue(reasonLine.getContents() instanceof TranslatableContents);
        assertEquals("food_reason.muslimqol.plant_based", ((TranslatableContents) reasonLine.getContents()).getKey());
    }

    @Test
    void testTooltipFormatterMapsRestrictedCorrectly() {
        assertTrue(FoodClassificationTooltipFormatter.shouldShowTooltip(porkId));

        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(porkId);
        assertFalse(lines.isEmpty(), "Restricted food must produce tooltip lines");

        Component statusLine = lines.get(1);
        assertEquals(ChatFormatting.RED, FoodClassificationTooltipFormatter.getStatusColor(FoodStatus.RESTRICTED));
        assertTrue(statusLine.getContents() instanceof TranslatableContents);
        assertEquals("food_status.muslimqol.restricted", ((TranslatableContents) statusLine.getContents()).getKey());

        Component reasonLine = lines.get(2);
        assertTrue(reasonLine.getContents() instanceof TranslatableContents);
        assertEquals("food_reason.muslimqol.swine", ((TranslatableContents) reasonLine.getContents()).getKey());

        // Must include policy warning line
        boolean hasPolicyLine = lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents tc
                && tc.getKey().equals("food_policy.muslimqol.policy_line"));
        assertTrue(hasPolicyLine, "Restricted food must include policy restriction line");
    }

    @Test
    void testTooltipFormatterMapsDoubtfulCorrectly() {
        FoodClassification doubtful = new FoodClassification(FoodStatus.DOUBTFUL, "doubtful_ingredient", ClassificationSource.DATAPACK);
        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(doubtful);

        assertFalse(lines.isEmpty());
        Component statusLine = lines.get(1);
        assertEquals(ChatFormatting.GOLD, FoodClassificationTooltipFormatter.getStatusColor(FoodStatus.DOUBTFUL));
        assertTrue(statusLine.getContents() instanceof TranslatableContents);
        assertEquals("food_status.muslimqol.doubtful", ((TranslatableContents) statusLine.getContents()).getKey());

        Component reasonLine = lines.get(2);
        assertTrue(reasonLine.getContents() instanceof TranslatableContents);
        assertEquals("food_reason.muslimqol.doubtful_ingredient", ((TranslatableContents) reasonLine.getContents()).getKey());

        boolean hasPolicyLine = lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents tc
                && tc.getKey().equals("food_policy.muslimqol.policy_line"));
        assertTrue(hasPolicyLine, "Doubtful food must include policy line");
    }

    @Test
    void testTooltipFormatterMapsUnknownCorrectly() {
        FoodClassification unknown = FoodClassification.unknown();
        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(unknown);

        assertFalse(lines.isEmpty());
        Component statusLine = lines.get(1);
        assertEquals(ChatFormatting.GRAY, FoodClassificationTooltipFormatter.getStatusColor(FoodStatus.UNKNOWN));
        assertTrue(statusLine.getContents() instanceof TranslatableContents);
        assertEquals("food_status.muslimqol.unknown", ((TranslatableContents) statusLine.getContents()).getKey());
    }

    @Test
    void testNonFoodDoesNotGenerateTooltipClutter() {
        assertFalse(FoodClassificationTooltipFormatter.shouldShowTooltip(stoneId),
                "Non-food blocks/items must not be marked as needing food tooltips");

        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(stoneId);
        assertTrue(lines.isEmpty(), "Non-food items must produce zero MuslimQoL tooltip lines in JEI/inventory");
    }

    @Test
    void testNullOrEmptyStackDoesNotGenerateTooltip() {
        assertFalse(FoodClassificationTooltipFormatter.shouldShowTooltip((ItemStack) null));
        assertFalse(FoodClassificationTooltipFormatter.shouldShowTooltip((ResourceLocation) null));
        assertFalse(FoodClassificationTooltipFormatter.shouldShowTooltip(ItemStack.EMPTY));

        assertTrue(FoodClassificationTooltipFormatter.formatTooltip((ItemStack) null).isEmpty());
        assertTrue(FoodClassificationTooltipFormatter.formatTooltip((ResourceLocation) null).isEmpty());
        assertTrue(FoodClassificationTooltipFormatter.formatTooltip(ItemStack.EMPTY).isEmpty());
        assertTrue(FoodClassificationTooltipFormatter.formatTooltip((FoodClassification) null).isEmpty());
    }

    @Test
    void testTooltipFormatterReflectsCoreFoodClassifierDirectly() {
        // Built-in status is HALAL
        List<Component> initialLines = FoodClassificationTooltipFormatter.formatTooltip(appleId);
        assertEquals("food_status.muslimqol.halal", ((TranslatableContents) initialLines.get(1).getContents()).getKey());

        // Datapack override to DOUBTFUL
        FoodClassificationRegistry.registerDatapackEntry(
                appleId,
                new FoodClassification(FoodStatus.DOUBTFUL, "datapack_concern", ClassificationSource.DATAPACK)
        );

        // Tooltip immediately reflects updated classification without restart or stale cache
        List<Component> updatedLines = FoodClassificationTooltipFormatter.formatTooltip(appleId);
        assertEquals("food_status.muslimqol.doubtful", ((TranslatableContents) updatedLines.get(1).getContents()).getKey());
        assertEquals("food_reason.muslimqol.datapack_concern", ((TranslatableContents) updatedLines.get(2).getContents()).getKey());
    }

    @Test
    void testJeiPluginContractAndUid() {
        try {
            Class.forName("mezz.jei.api.IModPlugin");
        } catch (ClassNotFoundException e) {
            // JEI classes not present on runtime classpath (e.g. running in no-JEI environment)
            return;
        }

        MuslimQolJeiPlugin plugin = new MuslimQolJeiPlugin();
        assertNotNull(plugin.getPluginUid());
        assertEquals("muslimqol:jei", plugin.getPluginUid().toString());
        assertEquals(MuslimQolJeiPlugin.PLUGIN_UID, plugin.getPluginUid());

        // Verify runtime lifecycle callback completes without exception
        plugin.onRuntimeAvailable(null);
    }
}
