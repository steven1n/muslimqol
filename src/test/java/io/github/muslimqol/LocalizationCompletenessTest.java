package io.github.muslimqol;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodStatus;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationCompletenessTest {

    @Test
    void testLanguageFilesParity() {
        Gson gson = new Gson();
        var mapType = new TypeToken<Map<String, String>>() {}.getType();

        InputStream enStream = getClass().getResourceAsStream("/assets/muslimqol/lang/en_us.json");
        assertNotNull(enStream, "en_us.json must exist in assets/muslimqol/lang/");
        Map<String, String> enMap = gson.fromJson(new InputStreamReader(enStream, StandardCharsets.UTF_8), mapType);

        InputStream arStream = getClass().getResourceAsStream("/assets/muslimqol/lang/ar_sa.json");
        assertNotNull(arStream, "ar_sa.json must exist in assets/muslimqol/lang/");
        Map<String, String> arMap = gson.fromJson(new InputStreamReader(arStream, StandardCharsets.UTF_8), mapType);

        assertFalse(enMap.isEmpty(), "en_us.json must not be empty");
        assertFalse(arMap.isEmpty(), "ar_sa.json must not be empty");

        // Every key in en_us must be translated in ar_sa
        for (String key : enMap.keySet()) {
            assertTrue(arMap.containsKey(key), "Missing Arabic translation for key: " + key);
            assertFalse(arMap.get(key).isBlank(), "Arabic translation is blank for key: " + key);
        }

        // Key counts must match
        assertEquals(enMap.keySet(), arMap.keySet(), "Key sets between en_us and ar_sa should be identical");
    }

    @Test
    void testFoodStatusKeysExist() {
        Gson gson = new Gson();
        var mapType = new TypeToken<Map<String, String>>() {}.getType();

        InputStream enStream = getClass().getResourceAsStream("/assets/muslimqol/lang/en_us.json");
        Map<String, String> enMap = gson.fromJson(new InputStreamReader(enStream, StandardCharsets.UTF_8), mapType);

        for (FoodStatus status : FoodStatus.values()) {
            assertTrue(enMap.containsKey(status.getTranslationKey()),
                    "Missing translation for FoodStatus: " + status.name());
        }
    }

    @Test
    void testConsumptionPolicyKeysExist() {
        Gson gson = new Gson();
        var mapType = new TypeToken<Map<String, String>>() {}.getType();

        InputStream enStream = getClass().getResourceAsStream("/assets/muslimqol/lang/en_us.json");
        Map<String, String> enMap = gson.fromJson(new InputStreamReader(enStream, StandardCharsets.UTF_8), mapType);

        for (ConsumptionPolicy policy : ConsumptionPolicy.values()) {
            assertTrue(enMap.containsKey(policy.getTranslationKey()),
                    "Missing translation for ConsumptionPolicy: " + policy.name());
        }
    }
}
