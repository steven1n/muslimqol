package io.github.muslimqol;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.data.FoodClassificationJsonLoader;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackJsonLoaderTest {

    @Test
    void testSingleItemJsonParsing() {
        String json = """
                {
                    "item": "examplemod:date_fruit",
                    "status": "HALAL",
                    "reason": "plant_based"
                }
                """;
        JsonElement element = JsonParser.parseString(json);
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        map.put(ResourceLocation.parse("examplemod:date_fruit"), element);

        Map<ResourceLocation, FoodClassification> results = FoodClassificationJsonLoader.parseAll(map);

        assertEquals(1, results.size());
        ResourceLocation id = ResourceLocation.parse("examplemod:date_fruit");
        FoodClassification classification = results.get(id);
        assertNotNull(classification);
        assertEquals(FoodStatus.HALAL, classification.status());
        assertEquals("plant_based", classification.reason());
        assertEquals(ClassificationSource.DATAPACK, classification.source());
    }

    @Test
    void testArrayJsonParsing() {
        String json = """
                {
                    "values": [
                        {
                            "item": "examplemod:pork_pie",
                            "status": "RESTRICTED",
                            "reason": "swine"
                        },
                        {
                            "item": "examplemod:mystery_soup",
                            "status": "DOUBTFUL",
                            "reason": "unknown_ingredients"
                        }
                    ]
                }
                """;
        JsonElement element = JsonParser.parseString(json);
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        map.put(ResourceLocation.parse("examplemod:pack_items"), element);

        Map<ResourceLocation, FoodClassification> results = FoodClassificationJsonLoader.parseAll(map);

        assertEquals(2, results.size());
        FoodClassification porkPie = results.get(ResourceLocation.parse("examplemod:pork_pie"));
        assertNotNull(porkPie);
        assertEquals(FoodStatus.RESTRICTED, porkPie.status());
        assertEquals("swine", porkPie.reason());

        FoodClassification mystery = results.get(ResourceLocation.parse("examplemod:mystery_soup"));
        assertNotNull(mystery);
        assertEquals(FoodStatus.DOUBTFUL, mystery.status());
        assertEquals("unknown_ingredients", mystery.reason());
    }

    @Test
    void testMapEntriesJsonParsing() {
        String json = """
                {
                    "entries": {
                        "examplemod:certified_beef": {
                            "status": "HALAL",
                            "reason": "datapack_certified"
                        }
                    }
                }
                """;
        JsonElement element = JsonParser.parseString(json);
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        map.put(ResourceLocation.parse("examplemod:entries_test"), element);

        Map<ResourceLocation, FoodClassification> results = FoodClassificationJsonLoader.parseAll(map);

        assertEquals(1, results.size());
        FoodClassification beef = results.get(ResourceLocation.parse("examplemod:certified_beef"));
        assertNotNull(beef);
        assertEquals(FoodStatus.HALAL, beef.status());
        assertEquals("datapack_certified", beef.reason());
    }

    @Test
    void testInvalidJsonGracefulSkip() {
        String invalidJson = """
                {
                    "item": "examplemod:bad_item",
                    "status": "NON_EXISTENT_STATUS"
                }
                """;
        JsonElement element = JsonParser.parseString(invalidJson);
        Map<ResourceLocation, JsonElement> map = new HashMap<>();
        map.put(ResourceLocation.parse("examplemod:bad_test"), element);

        Map<ResourceLocation, FoodClassification> results = FoodClassificationJsonLoader.parseAll(map);
        assertTrue(results.isEmpty());
    }
}
