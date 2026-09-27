package io.github.muslimqol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.config.CommonConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class QiblaClientConfigTest {

    @Test
    public void testClientConfigQiblaDefaults() {
        assertNotNull(ClientConfig.QIBLA_ENABLED);
        assertTrue(ClientConfig.QIBLA_ENABLED.getDefault());

        assertNotNull(ClientConfig.QIBLA_LOCATION_CONFIGURED);
        assertFalse(ClientConfig.QIBLA_LOCATION_CONFIGURED.getDefault());

        assertNotNull(ClientConfig.QIBLA_LATITUDE);
        assertEquals(0.0, ClientConfig.QIBLA_LATITUDE.getDefault(), 1e-6);

        assertNotNull(ClientConfig.QIBLA_LONGITUDE);
        assertEquals(0.0, ClientConfig.QIBLA_LONGITUDE.getDefault(), 1e-6);

        assertNotNull(ClientConfig.QIBLA_HUD_ENABLED);
        assertTrue(ClientConfig.QIBLA_HUD_ENABLED.getDefault());
    }

    @Test
    public void testCommonConfigDoesNotContainObserverCoordinates() {
        // Observer coordinates are personal data and must NEVER be stored in server/common configuration
        String specString = CommonConfig.SPEC.toString().toLowerCase();
        assertFalse(specString.contains("latitude"), "CommonConfig must not contain latitude");
        assertFalse(specString.contains("longitude"), "CommonConfig must not contain longitude");
        assertFalse(specString.contains("qibla"), "CommonConfig must not contain qibla settings");
    }

    @Test
    public void testQiblaLocalizationParity() throws Exception {
        JsonObject en;
        JsonObject ar;

        try (var enStream = getClass().getResourceAsStream("/assets/muslimqol/lang/en_us.json");
             var arStream = getClass().getResourceAsStream("/assets/muslimqol/lang/ar_sa.json")) {
            assertNotNull(enStream, "en_us.json must exist");
            assertNotNull(arStream, "ar_sa.json must exist");

            en = JsonParser.parseReader(new InputStreamReader(enStream, StandardCharsets.UTF_8)).getAsJsonObject();
            ar = JsonParser.parseReader(new InputStreamReader(arStream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        Set<String> requiredKeys = Set.of(
                "hud.muslimqol.qibla",
                "hud.muslimqol.qibla.aligned",
                "hud.muslimqol.qibla.behind",
                "config.muslimqol.qibla.enabled",
                "config.muslimqol.qibla.location_configured",
                "config.muslimqol.qibla.latitude",
                "config.muslimqol.qibla.longitude",
                "config.muslimqol.qibla.hud_enabled"
        );

        for (String key : requiredKeys) {
            assertTrue(en.has(key), "en_us.json missing key: " + key);
            assertTrue(ar.has(key), "ar_sa.json missing key: " + key);
            assertFalse(en.get(key).getAsString().isBlank(), "en_us value blank: " + key);
            assertFalse(ar.get(key).getAsString().isBlank(), "ar_sa value blank: " + key);
        }
    }
}
