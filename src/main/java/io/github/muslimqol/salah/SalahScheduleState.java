package io.github.muslimqol.salah;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of resolved obligatory Salah state at a specific evaluation instant.
 *
 * <p>Terminology note: {@code previousPrayer} (also accessible via {@link #lastStartedPrayer()})
 * indicates the most recently started obligatory prayer at or before {@code evaluatedAt}.
 * It does NOT assert a religious ruling that the prayer's valid performance window remains open.
 */
public record SalahScheduleState(
        Instant evaluatedAt,
        Optional<SalahEvent> previousPrayer,
        Optional<SalahEvent> nextPrayer
) {

    public SalahScheduleState {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        Objects.requireNonNull(previousPrayer, "previousPrayer must not be null");
        Objects.requireNonNull(nextPrayer, "nextPrayer must not be null");
    }

    /**
     * Alias for {@link #previousPrayer()} emphasizing that this represents the most recently
     * started obligatory prayer, not an active religious window determination.
     */
    public Optional<SalahEvent> lastStartedPrayer() {
        return previousPrayer;
    }

    /**
     * Returns the non-negative remaining duration until {@link #nextPrayer()}, or
     * {@link Optional#empty()} if no future obligatory prayer is available.
     */
    public Optional<Duration> remainingUntilNext() {
        if (nextPrayer.isEmpty()) {
            return Optional.empty();
        }
        Duration raw = Duration.between(evaluatedAt, nextPrayer.get().instant());
        return Optional.of(raw.isNegative() ? Duration.ZERO : raw);
    }

    /**
     * Creates an unavailable schedule state when no obligatory prayer events can be resolved.
     */
    public static SalahScheduleState unavailable(Instant evaluatedAt) {
        return new SalahScheduleState(evaluatedAt, Optional.empty(), Optional.empty());
    }
}
