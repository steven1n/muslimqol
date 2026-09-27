package io.github.muslimqol;

import io.github.muslimqol.qibla.GeoCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GeoCoordinateTest {

    @Test
    public void testValidCoordinates() {
        GeoCoordinate coord = GeoCoordinate.of(21.4225, 39.8262);
        assertEquals(21.4225, coord.latitudeDeg(), 1e-9);
        assertEquals(39.8262, coord.longitudeDeg(), 1e-9);
    }

    @Test
    public void testBoundaryCoordinates() {
        assertDoesNotThrow(() -> GeoCoordinate.of(90.0, 180.0));
        assertDoesNotThrow(() -> GeoCoordinate.of(-90.0, -180.0));
        assertDoesNotThrow(() -> GeoCoordinate.of(0.0, 0.0));
        assertDoesNotThrow(() -> GeoCoordinate.of(90.0, -180.0));
        assertDoesNotThrow(() -> GeoCoordinate.of(-90.0, 180.0));
    }

    @Test
    public void testInvalidLatitudeRejected() {
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(90.0001, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(-90.0001, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(120.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(-120.0, 0.0));
    }

    @Test
    public void testInvalidLongitudeRejected() {
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(0.0, 180.0001));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(0.0, -180.0001));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(0.0, 200.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(0.0, -200.0));
    }

    @Test
    public void testNaNAndInfinityRejected() {
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(0.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(0.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(Double.NEGATIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoCoordinate.of(0.0, Double.NEGATIVE_INFINITY));
    }
}
