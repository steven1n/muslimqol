package io.github.muslimqol;

import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.client.FoodClassificationTooltipFormatter;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link FoodClassificationTooltipFormatter} correctly formats food status tooltips
 * for MuslimQoL's classification pipeline.
 *
 * <p>These tests exercise MuslimQoL-owned presentation logic only. No JEI classes are required.
 * The tooltip formatter produces the same output when invoked by NeoForge's ItemTooltipEvent
 * (normal gameplay) or by any other recipe viewer that obtains tooltips through the standard
 * Minecraft/NeoForge tooltip pipeline.
 *
 * <p>Note on {@code shouldShowTooltip(ResourceLocation)}: this overload cannot detect arbitrary
 * modded edible items because food detection via {@code DataComponents.FOOD} requires an actual
 * {@code ItemStack}. It returns {@code true} only when the item is explicitly classified in
 * MuslimQoL's classification data. Tests therefore use known classified items (apple, porkchop)
 * from the built-in data set. For real UI rendering, prefer the {@code ItemStack} overload.
 */
class FoodTooltipCompatibilityTest {

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

    // ── status color mapping ───────────────────────────────────────────────────

    @Test
    void testStatusColorHalal() {
        assertEquals(ChatFormatting.GREEN, FoodClassificationTooltipFormatter.getStatusColor(FoodStatus.HALAL));
    }

    @Test
    void testStatusColorRestricted() {
        assertEquals(ChatFormatting.RED, FoodClassificationTooltipFormatter.getStatusColor(FoodStatus.RESTRICTED));
    }

    @Test
    void testStatusColorDoubtful() {
        assertEquals(ChatFormatting.GOLD, FoodClassificationTooltipFormatter.getStatusColor(FoodStatus.DOUBTFUL));
    }

    @Test
    void testStatusColorUnknown() {
        assertEquals(ChatFormatting.GRAY, FoodClassificationTooltipFormatter.getStatusColor(FoodStatus.UNKNOWN));
    }

    @Test
    void testStatusColorNull() {
        assertEquals(ChatFormatting.GRAY, FoodClassificationTooltipFormatter.getStatusColor(null));
    }

    // ── HALAL formatting ───────────────────────────────────────────────────────

    @Test
    void testTooltipFormatterMapsHalalCorrectly() {
        assertTrue(FoodClassificationTooltipFormatter.shouldShowTooltip(appleId));

        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(appleId);
        assertFalse(lines.isEmpty(), "Halal food must produce tooltip lines");

        // lines[0] = separator, lines[1] = status, lines[2] = reason
        Component statusLine = lines.get(1);
        assertTrue(statusLine.getContents() instanceof TranslatableContents);
        assertEquals("food_status.muslimqol.halal", ((TranslatableContents) statusLine.getContents()).getKey());

        Component reasonLine = lines.get(2);
        assertTrue(reasonLine.getContents() instanceof TranslatableContents);
        assertEquals("food_reason.muslimqol.plant_based", ((TranslatableContents) reasonLine.getContents()).getKey());
    }

    // ── RESTRICTED formatting ──────────────────────────────────────────────────

    @Test
    void testTooltipFormatterMapsRestrictedCorrectly() {
        assertTrue(FoodClassificationTooltipFormatter.shouldShowTooltip(porkId));

        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(porkId);
        assertFalse(lines.isEmpty(), "Restricted food must produce tooltip lines");

        Component statusLine = lines.get(1);
        assertTrue(statusLine.getContents() instanceof TranslatableContents);
        assertEquals("food_status.muslimqol.restricted", ((TranslatableContents) statusLine.getContents()).getKey());

        Component reasonLine = lines.get(2);
        assertTrue(reasonLine.getContents() instanceof TranslatableContents);
        assertEquals("food_reason.muslimqol.swine", ((TranslatableContents) reasonLine.getContents()).getKey());

        // Must include policy line (BLOCK policy for RESTRICTED)
        boolean hasPolicyLine = lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents tc
                && tc.getKey().equals("food_policy.muslimqol.policy_line"));
        assertTrue(hasPolicyLine, "Restricted food must include policy restriction line");
    }

    // ── DOUBTFUL formatting ────────────────────────────────────────────────────

    @Test
    void testTooltipFormatterMapsDoubtfulCorrectly() {
        FoodClassification doubtful = new FoodClassification(FoodStatus.DOUBTFUL, "doubtful_ingredient", ClassificationSource.DATAPACK);
        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(doubtful);

        assertFalse(lines.isEmpty());
        Component statusLine = lines.get(1);
        assertTrue(statusLine.getContents() instanceof TranslatableContents);
        assertEquals("food_status.muslimqol.doubtful", ((TranslatableContents) statusLine.getContents()).getKey());

        Component reasonLine = lines.get(2);
        assertTrue(reasonLine.getContents() instanceof TranslatableContents);
        assertEquals("food_reason.muslimqol.doubtful_ingredient", ((TranslatableContents) reasonLine.getContents()).getKey());

        boolean hasPolicyLine = lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents tc
                && tc.getKey().equals("food_policy.muslimqol.policy_line"));
        assertTrue(hasPolicyLine, "Doubtful food must include policy warning line");
    }

    // ── UNKNOWN formatting ─────────────────────────────────────────────────────

    @Test
    void testTooltipFormatterMapsUnknownCorrectly() {
        FoodClassification unknown = FoodClassification.unknown();
        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(unknown);

        assertFalse(lines.isEmpty());
        Component statusLine = lines.get(1);
        assertTrue(statusLine.getContents() instanceof TranslatableContents);
        assertEquals("food_status.muslimqol.unknown", ((TranslatableContents) statusLine.getContents()).getKey());
    }

    // ── non-food suppression ───────────────────────────────────────────────────

    @Test
    void testNonFoodDoesNotGenerateTooltipClutter() {
        // stone is not food and has no explicit classification
        assertFalse(FoodClassificationTooltipFormatter.shouldShowTooltip(stoneId),
                "Non-food items must not be marked as needing food tooltips");

        List<Component> lines = FoodClassificationTooltipFormatter.formatTooltip(stoneId);
        assertTrue(lines.isEmpty(), "Non-food items must produce zero MuslimQoL tooltip lines");
    }

    // ── null/empty safety ──────────────────────────────────────────────────────

    @Test
    void testNullOrEmptyInputProducesNoTooltip() {
        assertFalse(FoodClassificationTooltipFormatter.shouldShowTooltip((ItemStack) null));
        assertFalse(FoodClassificationTooltipFormatter.shouldShowTooltip((ResourceLocation) null));
        assertFalse(FoodClassificationTooltipFormatter.shouldShowTooltip(ItemStack.EMPTY));

        assertTrue(FoodClassificationTooltipFormatter.formatTooltip((ItemStack) null).isEmpty());
        assertTrue(FoodClassificationTooltipFormatter.formatTooltip((ResourceLocation) null).isEmpty());
        assertTrue(FoodClassificationTooltipFormatter.formatTooltip(ItemStack.EMPTY).isEmpty());
        assertTrue(FoodClassificationTooltipFormatter.formatTooltip((FoodClassification) null).isEmpty());
    }

    // ── dynamic classification state / no stale caching ───────────────────────

    @Test
    void testTooltipReflectsLiveClassificationWithoutStaleCaching() {
        // Initial built-in state: apple is HALAL
        List<Component> initialLines = FoodClassificationTooltipFormatter.formatTooltip(appleId);
        assertEquals("food_status.muslimqol.halal",
                ((TranslatableContents) initialLines.get(1).getContents()).getKey());

        // Simulate datapack override to DOUBTFUL (e.g. after /reload)
        FoodClassificationRegistry.registerDatapackEntry(
                appleId,
                new FoodClassification(FoodStatus.DOUBTFUL, "datapack_concern", ClassificationSource.DATAPACK)
        );

        // Next hover must reflect updated state immediately — no stale cache
        List<Component> updatedLines = FoodClassificationTooltipFormatter.formatTooltip(appleId);
        assertEquals("food_status.muslimqol.doubtful",
                ((TranslatableContents) updatedLines.get(1).getContents()).getKey());
        assertEquals("food_reason.muslimqol.datapack_concern",
                ((TranslatableContents) updatedLines.get(2).getContents()).getKey());
    }
}
