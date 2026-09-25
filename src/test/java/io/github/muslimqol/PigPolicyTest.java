package io.github.muslimqol;

import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.api.PigPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PigPolicyTest {

    @Test
    void testPigPolicySemantics() {
        assertEquals("NORMAL", PigPolicy.NORMAL.name());
        assertEquals("NO_NATURAL_SPAWN", PigPolicy.NO_NATURAL_SPAWN.name());
        assertEquals("NO_PORK_DROPS", PigPolicy.NO_PORK_DROPS.name());
        assertEquals("DISABLED_GAMEPLAY", PigPolicy.DISABLED_GAMEPLAY.name());

        for (PigPolicy policy : PigPolicy.values()) {
            assertNotNull(policy);
        }
    }

    @Test
    void testSpawnSuppressionLogic() {
        // Only NO_NATURAL_SPAWN and DISABLED_GAMEPLAY suppress spawning
        assertFalse(shouldSuppressSpawning(PigPolicy.NORMAL));
        assertTrue(shouldSuppressSpawning(PigPolicy.NO_NATURAL_SPAWN));
        assertFalse(shouldSuppressSpawning(PigPolicy.NO_PORK_DROPS));
        assertTrue(shouldSuppressSpawning(PigPolicy.DISABLED_GAMEPLAY));
    }

    @Test
    void testDropSuppressionLogic() {
        // Only NO_PORK_DROPS and DISABLED_GAMEPLAY suppress pork drops
        assertFalse(shouldSuppressDrops(PigPolicy.NORMAL));
        assertFalse(shouldSuppressDrops(PigPolicy.NO_NATURAL_SPAWN));
        assertTrue(shouldSuppressDrops(PigPolicy.NO_PORK_DROPS));
        assertTrue(shouldSuppressDrops(PigPolicy.DISABLED_GAMEPLAY));
    }

    private boolean shouldSuppressSpawning(PigPolicy policy) {
        return policy == PigPolicy.NO_NATURAL_SPAWN || policy == PigPolicy.DISABLED_GAMEPLAY;
    }

    private boolean shouldSuppressDrops(PigPolicy policy) {
        return policy == PigPolicy.NO_PORK_DROPS || policy == PigPolicy.DISABLED_GAMEPLAY;
    }
}
