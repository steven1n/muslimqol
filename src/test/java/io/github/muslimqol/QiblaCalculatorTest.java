package io.github.muslimqol;

import io.github.muslimqol.qibla.GeoCoordinate;
import io.github.muslimqol.qibla.QiblaCalculator;
import io.github.muslimqol.qibla.QiblaResult;
import io.github.muslimqol.qibla.QiblaResultReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QiblaCalculatorTest {

    private static final double TOLERANCE_DEG = 0.2;

    @Test
    public void testReferenceBearingLondon() {
        // London: 51.5074 N, 0.1278 W -> expected ~118.99°
        GeoCoordinate london = GeoCoordinate.of(51.5074, -0.1278);
        QiblaResult result = QiblaCalculator.calculate(london);

        assertTrue(result.defined());
        assertEquals(QiblaResultReason.OK, result.reason());
        assertEquals(118.99, result.bearingDeg(), TOLERANCE_DEG);
    }

    @Test
    public void testReferenceBearingNewYork() {
        // New York: 40.7128 N, 74.0060 W -> expected ~58.48°
        GeoCoordinate newYork = GeoCoordinate.of(40.7128, -74.0060);
        QiblaResult result = QiblaCalculator.calculate(newYork);

        assertTrue(result.defined());
        assertEquals(QiblaResultReason.OK, result.reason());
        assertEquals(58.48, result.bearingDeg(), TOLERANCE_DEG);
    }

    @Test
    public void testReferenceBearingJakarta() {
        // Jakarta: 6.2088 S, 106.8456 E -> expected ~295.15°
        GeoCoordinate jakarta = GeoCoordinate.of(-6.2088, 106.8456);
        QiblaResult result = QiblaCalculator.calculate(jakarta);

        assertTrue(result.defined());
        assertEquals(QiblaResultReason.OK, result.reason());
        assertEquals(295.15, result.bearingDeg(), TOLERANCE_DEG);
    }

    @Test
    public void testReferenceBearingTokyo() {
        // Tokyo: 35.6762 N, 139.6503 E -> expected ~293.00°
        GeoCoordinate tokyo = GeoCoordinate.of(35.6762, 139.6503);
        QiblaResult result = QiblaCalculator.calculate(tokyo);

        assertTrue(result.defined());
        assertEquals(QiblaResultReason.OK, result.reason());
        assertEquals(293.00, result.bearingDeg(), TOLERANCE_DEG);
    }

    @Test
    public void testReferenceBearingSingapore() {
        // Singapore: 1.3521 N, 103.8198 E -> expected ~293.02°
        GeoCoordinate singapore = GeoCoordinate.of(1.3521, 103.8198);
        QiblaResult result = QiblaCalculator.calculate(singapore);

        assertTrue(result.defined());
        assertEquals(QiblaResultReason.OK, result.reason());
        assertEquals(293.02, result.bearingDeg(), TOLERANCE_DEG);
    }

    @Test
    public void testObserverAtKaabaIsUndefined() {
        GeoCoordinate atKaaba = GeoCoordinate.of(QiblaCalculator.KAABA_LATITUDE_DEG, QiblaCalculator.KAABA_LONGITUDE_DEG);
        QiblaResult result = QiblaCalculator.calculate(atKaaba);

        assertFalse(result.defined());
        assertEquals(QiblaResultReason.AT_KAABA, result.reason());
        assertTrue(Double.isNaN(result.bearingDeg()));
        assertTrue(QiblaCalculator.isAtKaaba(atKaaba));
    }

    @Test
    public void testObserverAtAntipodalIsUndefined() {
        // Antipodal: -21.4225 N, 39.8262 - 180 = -140.1738
        GeoCoordinate antipodal = GeoCoordinate.of(-QiblaCalculator.KAABA_LATITUDE_DEG, QiblaCalculator.KAABA_LONGITUDE_DEG - 180.0);
        QiblaResult result = QiblaCalculator.calculate(antipodal);

        assertFalse(result.defined());
        assertEquals(QiblaResultReason.ANTIPODAL, result.reason());
        assertTrue(Double.isNaN(result.bearingDeg()));
        assertTrue(QiblaCalculator.isAntipodal(antipodal));
    }

    @Test
    public void testPolesBearing() {
        // North Pole
        GeoCoordinate northPole = GeoCoordinate.of(90.0, 0.0);
        QiblaResult npResult = QiblaCalculator.calculate(northPole);
        assertTrue(npResult.defined());
        assertTrue(npResult.bearingDeg() >= 0.0 && npResult.bearingDeg() < 360.0);

        // South Pole
        GeoCoordinate southPole = GeoCoordinate.of(-90.0, 0.0);
        QiblaResult spResult = QiblaCalculator.calculate(southPole);
        assertTrue(spResult.defined());
        assertTrue(spResult.bearingDeg() >= 0.0 && spResult.bearingDeg() < 360.0);
    }

    @Test
    public void testLongitudeExtremes() {
        GeoCoordinate westExtreme = GeoCoordinate.of(0.0, -180.0);
        GeoCoordinate eastExtreme = GeoCoordinate.of(0.0, 180.0);

        QiblaResult r1 = QiblaCalculator.calculate(westExtreme);
        QiblaResult r2 = QiblaCalculator.calculate(eastExtreme);

        assertTrue(r1.defined());
        assertTrue(r2.defined());
        assertEquals(r1.bearingDeg(), r2.bearingDeg(), 1e-6);
    }

    @Test
    public void testGlobalGridInvariants() {
        for (double lat = -80.0; lat <= 80.0; lat += 20.0) {
            for (double lon = -160.0; lon <= 160.0; lon += 40.0) {
                GeoCoordinate coord = GeoCoordinate.of(lat, lon);
                QiblaResult result = QiblaCalculator.calculate(coord);
                if (result.defined()) {
                    assertTrue(result.bearingDeg() >= 0.0, "Bearing must be >= 0: " + result.bearingDeg());
                    assertTrue(result.bearingDeg() < 360.0, "Bearing must be < 360: " + result.bearingDeg());
                    assertEquals(QiblaResultReason.OK, result.reason());
                }
            }
        }
    }
}
