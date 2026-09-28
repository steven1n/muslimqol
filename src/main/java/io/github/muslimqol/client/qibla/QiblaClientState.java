package io.github.muslimqol.client.qibla;

/**
 * Immutable client-side state containing calculated Qibla angles and alignment status for rendering.
 *
 * @param available          true if observer location is configured and Qibla bearing is valid
 * @param absoluteBearingDeg geographic Qibla bearing in degrees [0, 360), or NaN if unavailable
 * @param playerHeadingDeg   player's Minecraft-derived geographic heading in degrees [0, 360), or NaN
 * @param relativeAngleDeg   signed relative angle in degrees [-180, 180) from heading to Qibla (positive = right, negative = left)
 * @param aligned            true if player is facing within alignment tolerance of Qibla
 */
public record QiblaClientState(
        boolean available,
        double absoluteBearingDeg,
        double playerHeadingDeg,
        double relativeAngleDeg,
        boolean aligned
) {

    public static final QiblaClientState UNAVAILABLE = new QiblaClientState(
            false,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            false
    );

    public static QiblaClientState of(
            double absoluteBearingDeg,
            double playerHeadingDeg,
            double relativeAngleDeg,
            boolean aligned
    ) {
        return new QiblaClientState(true, absoluteBearingDeg, playerHeadingDeg, relativeAngleDeg, aligned);
    }
}
