# Prayer Time Calculation System

MuslimQoL 0.4 introduces a deterministic, offline, privacy-preserving daily prayer-time calculation core and client schedule service.

> [!IMPORTANT]
> MuslimQoL provides configurable prayer-time calculations. It is not a religious authority and does not replace local mosque/community guidance.

---

## 1. Overview

MuslimQoL 0.4 computes the six daily solar and prayer time events for an observer's configured real-world coordinates and local civil date:

| Event | Enum | Obligatory (`isObligatoryPrayer`) | Astronomical Definition |
| :--- | :--- | :---: | :--- |
| **Fajr** | `Prayer.FAJR` | `true` | Morning twilight when solar altitude reaches $-\alpha_{\text{fajr}}$ before solar noon |
| **Sunrise** | `Prayer.SUNRISE` | `false` | Upper solar limb + atmospheric refraction ($\alpha = -0.833^\circ$) before solar noon |
| **Dhuhr** | `Prayer.DHUHR` | `true` | Solar transit (upper meridian passage / solar noon) |
| **Asr** | `Prayer.ASR` | `true` | Afternoon shadow-ratio altitude ($k = 1$ Standard or $k = 2$ Hanafi) after solar noon |
| **Maghrib** | `Prayer.MAGHRIB` | `true` | Sunset ($\alpha = -0.833^\circ$) after solar noon |
| **Isha** | `Prayer.ISHA` | `true` | Evening twilight when solar altitude reaches $-\alpha_{\text{isha}}$ after solar noon |

### Scope of 0.4

MuslimQoL 0.4 provides the **calculation core, client configuration, and client schedule service only**. It does not add a prayer HUD, countdown overlay, toast/chat notifications, or audio cues (which belong to 0.5 Salah Notifications).

---

## 2. Mathematical Model

All calculations are implemented locally in `io.github.muslimqol.prayer.astronomy` using standard `java.lang.Math` and `java.time` APIs (NOAA Solar Calculator / Jean Meeus *Astronomical Algorithms* Ch. 25 equations) with zero external runtime dependencies.

### 2.1 Julian Day & Julian Century

For a calendar date $(Y, M, D)$ at $00:00\text{ UTC}$ (with January/February treated as months $13/14$ of year $Y - 1$):

$$A = \lfloor Y / 100 \rfloor, \quad B = 2 - A + \lfloor A / 4 \rfloor$$

$$JD_0 = \lfloor 365.25 (Y + 4716) \rfloor + \lfloor 30.6001 (M + 1) \rfloor + D + B - 1524.5$$

At fractional UTC hour $h$:

$$JD = JD_0 + \frac{h}{24}, \quad T = \frac{JD - 2451545.0}{36525.0}$$

### 2.2 Solar Declination ($\delta$) & Equation of Time ($EoT$)

From Julian century $T$, the geometric mean longitude $L_0$, mean anomaly $M$, eccentricity $e$, equation of center $C$, apparent longitude $\lambda$, and true obliquity $\varepsilon$ yield:

- **Solar Declination ($\delta$)**:
  $$\sin(\delta) = \sin(\varepsilon)\sin(\lambda)$$
- **Equation of Time ($EoT$, in minutes)**:
  $$y = \tan^2\left(\frac{\varepsilon}{2}\right)$$
  $$EoT = 4 \cdot \operatorname{deg}\!\left(y\sin(2L_0) - 2e\sin(M) + 4e y\sin(M)\cos(2L_0) - \frac{1}{2}y^2\sin(4L_0) - \frac{5}{4}e^2\sin(2M)\right)$$

### 2.3 Solar Transit (Dhuhr)

Solar noon is refined iteratively over 3 passes starting from $t_0 = 12.0 - \frac{\text{longitude}}{15.0}$ hours relative to `civilDate` `00:00:00Z`:

$$T_{\text{noon}} = 12.0 - \frac{\text{longitude}}{15.0} - \frac{EoT(t)}{60.0}$$

### 2.4 Solar Hour Angle & Sunrise / Sunset / Twilight

For target solar altitude $\alpha$ (in degrees), observer latitude $\phi$, and solar declination $\delta$:

$$\cos(H) = \frac{\sin(\alpha) - \sin(\phi)\sin(\delta)}{\cos(\phi)\cos(\delta)}$$

If $|\cos(H)| > 1.0$, the sun never reaches altitude $\alpha$ on that date (high-latitude twilight persistence or polar day/night). Otherwise, $H = \arccos(\cos H)$ in degrees ($[0^\circ, 180^\circ]$), and the event time is refined over 3 iterations:

$$t = T_{\text{noon}}(t) \mp \frac{H(\alpha, \delta(t))}{15.0}$$

Sunrise and Maghrib use the standard apparent horizon altitude $\alpha = -0.833^\circ$ (accounting for the $0.266^\circ$ solar semi-diameter and $0.567^\circ$ atmospheric refraction).

### 2.5 Asr Shadow-Ratio Altitude

Given shadow factor $k \in \{1, 2\}$, observer latitude $\phi$, and solar declination $\delta$ at the afternoon candidate time:

$$\cot(\alpha_{\text{asr}}) = k + \tan(|\phi - \delta|) \implies \alpha_{\text{asr}} = \arctan\!\left(\frac{1}{k + \tan(|\phi - \delta|)}\right)$$

The afternoon hour angle $H(\alpha_{\text{asr}})$ is then solved iteratively after solar noon.

### 2.6 Local Civil Date Anchoring Across Longitudes

Unwrapped solar hours relative to `civilDate` `00:00:00Z` (`12.0 - longitude / 15.0`) naturally fall below `0.0` for eastern longitudes (e.g., Tokyo / Jakarta / Singapore morning events occurring on the previous UTC day) and above `24.0` for western longitudes (e.g., New York evening events occurring on the next UTC day), ensuring all six calculated events belong to the requested local `civilDate` in the observer's `ZoneId`.

---

## 3. Supported Calculation Methods

| Method Enum | Fajr Angle ($\alpha_{\text{fajr}}$) | Isha Angle ($\alpha_{\text{isha}}$) | Authority / Convention |
| :--- | :---: | :---: | :--- |
| `MUSLIM_WORLD_LEAGUE` | `18.0°` | `17.0°` | Muslim World League (MWL) — default |
| `EGYPTIAN` | `19.5°` | `17.5°` | Egyptian General Authority of Survey |
| `KARACHI` | `18.0°` | `18.0°` | University of Islamic Sciences, Karachi |
| `NORTH_AMERICA` | `15.0°` | `15.0°` | Islamic Society of North America (ISNA) |
| `KUWAIT` | `18.0°` | `17.5°` | Kuwait convention |
| `SINGAPORE` | `20.0°` | `18.0°` | MUIS (Majlis Ugama Islam Singapura) |
| `DUBAI` | `18.2°` | `18.2°` | UAE / Dubai (`18.2° / 18.2°`) |
| `CUSTOM` | `custom_fajr_angle` | `custom_isha_angle` | User-specified angles within `[1.0°, 30.0°]` |

### Deferred Calculation Methods

The following methods are intentionally deferred from 0.4:

- **`UMM_AL_QURA`**: Uses a fixed 90-minute Isha interval during non-Ramadan months and 120 minutes during Ramadan. Deferred until Hijri calendar / Ramadan handling is introduced.
- **`QATAR`**: Fixed 90-minute Isha interval method; deferred alongside fixed-interval method support.
- **`MOONSIGHTING_COMMITTEE`**: Uses seasonal latitude-dependent twilight interpolation curves (`shafaq`) rather than fixed depression angles; deferred to a dedicated future release.

---

## 4. Asr Jurisprudential Methods

| Enum | Shadow Factor ($k$) | Description |
| :--- | :---: | :--- |
| `STANDARD` | `1` | Majority (Shafi'i, Maliki, Hanbali): shadow equals object length plus noon shadow |
| `HANAFI` | `2` | Hanafi: shadow equals twice object length plus noon shadow |

For any valid day where Asr is defined, `HANAFI` Asr always occurs strictly after `STANDARD` Asr.

---

## 5. High-Latitude Rules & Polar Regions

At high latitudes during summer, the sun may remain above $-\alpha_{\text{fajr}}$ or $-\alpha_{\text{isha}}$ throughout the night even when normal sunset and sunrise occur.

When astronomical Fajr or Isha cannot be solved (`hourAngle` is `NaN`), MuslimQoL computes the night duration from **today's astronomical sunset (Maghrib) to tomorrow's astronomical sunrise** (before manual minute adjustments):

$$\text{nightDuration} = t_{\text{nextSunrise}} - t_{\text{sunset}}$$

| Rule Enum | Portion of Night ($p$) | Fallback Fajr | Fallback Isha | Result Source |
| :--- | :---: | :--- | :--- | :--- |
| `NONE` | — | `UNAVAILABLE` | `UNAVAILABLE` | `PrayerTimeSource.UNAVAILABLE` |
| `MIDDLE_OF_NIGHT` | $1 / 2$ | $t_{\text{nextSunrise}} - p \cdot \text{nightDuration}$ | $t_{\text{sunset}} + p \cdot \text{nightDuration}$ | `PrayerTimeSource.HIGH_LATITUDE_ADJUSTED` |
| `SEVENTH_OF_NIGHT` | $1 / 7$ | $t_{\text{nextSunrise}} - p \cdot \text{nightDuration}$ | $t_{\text{sunset}} + p \cdot \text{nightDuration}$ | `PrayerTimeSource.HIGH_LATITUDE_ADJUSTED` |
| `TWILIGHT_ANGLE` | $\alpha / 60$ | $t_{\text{nextSunrise}} - \frac{\alpha_{\text{fajr}}}{60} \cdot \text{nightDuration}$ | $t_{\text{sunset}} + \frac{\alpha_{\text{isha}}}{60} \cdot \text{nightDuration}$ | `PrayerTimeSource.HIGH_LATITUDE_ADJUSTED` |

### Polar Day & Polar Night

During extreme polar summer (midnight sun) or polar winter (polar night) where sunrise or sunset itself does not occur:

- High-latitude night-fraction rules cannot be applied because no sunset-to-sunrise night interval exists.
- Unsolvable events return `PrayerMoment.unavailable()` (`Optional.empty()` timestamp with `PrayerTimeSource.UNAVAILABLE`).
- MuslimQoL never fabricates sentinel timestamps (`Instant.EPOCH`, `Instant.MIN`, `Instant.MAX`, or `null`).
- During polar day (e.g., Tromsø `2026-06-21`), Dhuhr and Asr remain `ASTRONOMICAL` while Sunrise, Maghrib, Fajr, and Isha are cleanly `UNAVAILABLE`.
- During polar night (e.g., Tromsø `2026-12-21`), Dhuhr remains `ASTRONOMICAL` while Sunrise, Asr, Maghrib, Fajr, and Isha are cleanly `UNAVAILABLE`.

---

## 6. Per-Prayer Minute Adjustments

Each event supports an independent integer minute offset in `[-60, +60]`:

- Adjustments are applied **after** astronomical or high-latitude calculation.
- Adjustments are never applied to `UNAVAILABLE` moments.
- Values outside `[-60, +60]` are rejected by `PrayerAdjustments`.

---

## 7. Timezone Behavior

- **`zone_id = ""` (default)**: Uses `ZoneId.systemDefault()`.
- **Explicit IANA Timezone**: Any valid `ZoneId` string (e.g., `"Europe/London"`, `"America/New_York"`, `"Asia/Tokyo"`, `"Asia/Riyadh"`) overrides the system timezone for determining the local civil date and converting moments via `PrayerTimes.zonedTime(Prayer)`.
- **Invalid `zone_id`**: If an invalid timezone identifier is configured, `PrayerTimesClientService` returns `Optional.empty()` (unavailable schedule) and logs a single warning without exposing observer coordinates.

---

## 8. Client Configuration

Prayer calculation settings are stored in `config/muslimqol-client.toml` alongside `[qibla]`:

```toml
[prayer]
    # Enable daily prayer time calculation on the client.
    enabled = true

    # Optional IANA timezone ID (e.g., 'Europe/London', 'America/New_York', 'Asia/Riyadh').
    # Leave blank to use the system default timezone.
    zone_id = ""

    # Prayer calculation method.
    # Options: MUSLIM_WORLD_LEAGUE, EGYPTIAN, KARACHI, NORTH_AMERICA, KUWAIT, SINGAPORE, DUBAI, CUSTOM
    calculation_method = "MUSLIM_WORLD_LEAGUE"

    # Custom Fajr twilight angle in degrees (1.0 to 30.0). Used only when calculation_method = "CUSTOM".
    custom_fajr_angle = 18.0

    # Custom Isha twilight angle in degrees (1.0 to 30.0). Used only when calculation_method = "CUSTOM".
    custom_isha_angle = 17.0

    # Asr juristic shadow ratio method. Options: STANDARD, HANAFI
    asr_method = "STANDARD"

    # High-latitude fallback rule when astronomical twilight does not occur.
    # Options: NONE, MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, TWILIGHT_ANGLE
    high_latitude_rule = "MIDDLE_OF_NIGHT"

    # Manual minute adjustments (-60 to +60) applied after calculation
    fajr_adjustment_minutes = 0
    sunrise_adjustment_minutes = 0
    dhuhr_adjustment_minutes = 0
    asr_adjustment_minutes = 0
    maghrib_adjustment_minutes = 0
    isha_adjustment_minutes = 0
```

> [!NOTE]
> Observer coordinates are shared with the `[qibla]` section (`qibla.location_configured`, `qibla.latitude`, `qibla.longitude`) via `io.github.muslimqol.qibla.GeoCoordinate`.

---

## 9. Privacy Guarantees

1. **Client-Only Storage**: Observer coordinates and prayer calculation parameters live strictly in `config/muslimqol-client.toml`.
2. **Zero Network Transmission**: Coordinates and prayer schedules are never sent in multiplayer packets or stored on servers.
3. **Zero External Network I/O**: All astronomical calculations run offline in pure Java.
4. **No Coordinate Leakage in Logs or `toString()`**: `PrayerTimes.toString()` and warning logs intentionally omit latitude and longitude.
