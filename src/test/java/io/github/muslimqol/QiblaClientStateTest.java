package io.github.muslimqol;

import io.github.muslimqol.client.qibla.QiblaClientService;
import io.github.muslimqol.client.qibla.QiblaClientState;
import io.github.muslimqol.qibla.QiblaResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QiblaClientStateTest {

    private static final double EPSILON = 1e-6;

    @Test
    public void testComputeStateFacingDirectly() {
        QiblaResult result = QiblaResult.ok(293.0);
        // Player yaw: heading = normalize360(180 + yaw).
        // For heading 293°, 180 + yaw = 293 -> yaw = 113.0°
        float playerYaw = 113.0f;

        QiblaClientState state = QiblaClientService.computeState(result, playerYaw, 3.0);

        assertTrue(state.available());
        assertEquals(293.0, state.absoluteBearingDeg(), EPSILON);
        assertEquals(293.0, state.playerHeadingDeg(), EPSILON);
        assertEquals(0.0, state.relativeAngleDeg(), EPSILON);
        assertTrue(state.aligned());
    }

    @Test
    public void testComputeStateFacingWest() {
        QiblaResult result = QiblaResult.ok(293.0);
        // Heading 270° (West) -> yaw 90°
        float playerYaw = 90.0f;

        QiblaClientState state = QiblaClientService.computeState(result, playerYaw, 3.0);

        assertTrue(state.available());
        assertEquals(293.0, state.absoluteBearingDeg(), EPSILON);
        assertEquals(270.0, state.playerHeadingDeg(), EPSILON);
        // Qibla is 23° to the player's right
        assertEquals(23.0, state.relativeAngleDeg(), EPSILON);
        assertFalse(state.aligned());
    }

    @Test
    public void testComputeStateFacingPastQibla() {
        QiblaResult result = QiblaResult.ok(293.0);
        // Heading 320° -> yaw 140°
        float playerYaw = 140.0f;

        QiblaClientState state = QiblaClientService.computeState(result, playerYaw, 3.0);

        assertTrue(state.available());
        assertEquals(293.0, state.absoluteBearingDeg(), EPSILON);
        assertEquals(320.0, state.playerHeadingDeg(), EPSILON);
        // Qibla is 27° to the player's left
        assertEquals(-27.0, state.relativeAngleDeg(), EPSILON);
        assertFalse(state.aligned());
    }

    @Test
    public void testComputeStateFacingOpposite() {
        QiblaResult result = QiblaResult.ok(293.0);
        // Opposite of 293° is 113° -> yaw: 180 + yaw = 113 -> yaw = -67°
        float playerYaw = -67.0f;

        QiblaClientState state = QiblaClientService.computeState(result, playerYaw, 3.0);

        assertTrue(state.available());
        assertEquals(293.0, state.absoluteBearingDeg(), EPSILON);
        assertEquals(113.0, state.playerHeadingDeg(), EPSILON);
        assertEquals(-180.0, state.relativeAngleDeg(), EPSILON);
        assertFalse(state.aligned());
    }

    @Test
    public void testComputeStateWithinTolerance() {
        QiblaResult result = QiblaResult.ok(293.0);
        // Heading 295.5° -> relative angle -2.5° -> within 3.0° tolerance
        // 180 + yaw = 295.5 -> yaw = 115.5
        float playerYaw = 115.5f;

        QiblaClientState state = QiblaClientService.computeState(result, playerYaw, 3.0);

        assertTrue(state.available());
        assertEquals(-2.5, state.relativeAngleDeg(), EPSILON);
        assertTrue(state.aligned());
    }

    @Test
    public void testComputeStateUnavailableForUndefinedResult() {
        QiblaClientState atKaabaState = QiblaClientService.computeState(QiblaResult.atKaaba(), 0.0f, 3.0);
        assertFalse(atKaabaState.available());
        assertTrue(Double.isNaN(atKaabaState.absoluteBearingDeg()));
        assertFalse(atKaabaState.aligned());

        QiblaClientState antipodalState = QiblaClientService.computeState(QiblaResult.antipodal(), 0.0f, 3.0);
        assertFalse(antipodalState.available());
        assertTrue(Double.isNaN(antipodalState.absoluteBearingDeg()));
        assertFalse(antipodalState.aligned());

        QiblaClientState nullState = QiblaClientService.computeState(null, 0.0f, 3.0);
        assertFalse(nullState.available());
        assertSame(QiblaClientState.UNAVAILABLE, nullState);
    }
}
