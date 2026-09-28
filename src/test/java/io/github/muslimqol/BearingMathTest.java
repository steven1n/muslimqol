package io.github.muslimqol;

import io.github.muslimqol.qibla.BearingMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BearingMathTest {

    private static final double EPSILON = 1e-9;

    @Test
    public void testNormalize360() {
        assertEquals(350.0, BearingMath.normalize360(-10.0), EPSILON);
        assertEquals(10.0, BearingMath.normalize360(370.0), EPSILON);
        assertEquals(0.0, BearingMath.normalize360(0.0), EPSILON);
        assertEquals(0.0, BearingMath.normalize360(360.0), EPSILON);
        assertEquals(0.0, BearingMath.normalize360(720.0), EPSILON);
        assertEquals(0.0, BearingMath.normalize360(-360.0), EPSILON);
        assertEquals(180.0, BearingMath.normalize360(-180.0), EPSILON);
        assertEquals(180.0, BearingMath.normalize360(180.0), EPSILON);
        assertEquals(270.0, BearingMath.normalize360(-90.0), EPSILON);
        assertEquals(90.0, BearingMath.normalize360(90.0), EPSILON);
    }

    @Test
    public void testNormalize360RejectsNonFinite() {
        assertThrows(IllegalArgumentException.class, () -> BearingMath.normalize360(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> BearingMath.normalize360(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> BearingMath.normalize360(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void testNormalizeSigned() {
        assertEquals(-10.0, BearingMath.normalizeSigned(350.0), EPSILON);
        assertEquals(-170.0, BearingMath.normalizeSigned(190.0), EPSILON);
        assertEquals(170.0, BearingMath.normalizeSigned(-190.0), EPSILON);
        assertEquals(-180.0, BearingMath.normalizeSigned(180.0), EPSILON);
        assertEquals(-180.0, BearingMath.normalizeSigned(-180.0), EPSILON);
        assertEquals(0.0, BearingMath.normalizeSigned(0.0), EPSILON);
        assertEquals(90.0, BearingMath.normalizeSigned(90.0), EPSILON);
        assertEquals(-90.0, BearingMath.normalizeSigned(-90.0), EPSILON);
    }

    @Test
    public void testPlayerYawToHeading() {
        // Minecraft yaw: 0=South, 90=West, -90=East, ±180=North
        assertEquals(0.0, BearingMath.playerYawToHeading(180.0), EPSILON);
        assertEquals(0.0, BearingMath.playerYawToHeading(-180.0), EPSILON);
        assertEquals(90.0, BearingMath.playerYawToHeading(-90.0), EPSILON);
        assertEquals(180.0, BearingMath.playerYawToHeading(0.0), EPSILON);
        assertEquals(270.0, BearingMath.playerYawToHeading(90.0), EPSILON);

        // Additional intermediate yaw tests
        assertEquals(45.0, BearingMath.playerYawToHeading(-135.0), EPSILON); // North-East
        assertEquals(135.0, BearingMath.playerYawToHeading(-45.0), EPSILON);  // South-East
        assertEquals(225.0, BearingMath.playerYawToHeading(45.0), EPSILON);   // South-West
        assertEquals(315.0, BearingMath.playerYawToHeading(135.0), EPSILON);  // North-West
    }

    @Test
    public void testCalculateRelativeAngle() {
        // Player facing Qibla
        assertEquals(0.0, BearingMath.calculateRelativeAngle(293.0, 293.0), EPSILON);

        // Qibla is to the right (positive)
        assertEquals(23.0, BearingMath.calculateRelativeAngle(293.0, 270.0), EPSILON);

        // Qibla is to the left (negative)
        assertEquals(-27.0, BearingMath.calculateRelativeAngle(293.0, 320.0), EPSILON);

        // Qibla is directly behind (-180°)
        assertEquals(-180.0, BearingMath.calculateRelativeAngle(293.0, 113.0), EPSILON);
        assertEquals(-180.0, BearingMath.calculateRelativeAngle(0.0, 180.0), EPSILON);
    }

    @Test
    public void testAlignmentTolerance() {
        assertTrue(BearingMath.isAligned(0.0, 3.0));
        assertTrue(BearingMath.isAligned(2.9, 3.0));
        assertTrue(BearingMath.isAligned(-2.9, 3.0));
        assertTrue(BearingMath.isAligned(3.0, 3.0));
        assertTrue(BearingMath.isAligned(-3.0, 3.0));

        assertFalse(BearingMath.isAligned(3.01, 3.0));
        assertFalse(BearingMath.isAligned(-3.01, 3.0));
        assertFalse(BearingMath.isAligned(45.0, 3.0));
        assertFalse(BearingMath.isAligned(-45.0, 3.0));

        // Default tolerance convenience method
        assertTrue(BearingMath.isAligned(0.0));
        assertTrue(BearingMath.isAligned(2.99));
        assertFalse(BearingMath.isAligned(3.01));
    }
}
