# Salah Experience (Next-Prayer HUD & Client Reminders)

MuslimQoL 0.5 builds on the frozen 0.4 prayer calculation engine to provide a client-private, offline next-prayer HUD indicator, countdown timer, advance reminders, and prayer-start Toast notifications.

> [!IMPORTANT]
> MuslimQoL provides configurable prayer-time utilities and reminders.
> It is not a religious authority and does not replace local mosque/community guidance.

---

## 1. Overview & Architecture

The 0.5 Salah Experience layer consumes calculated daily `PrayerTimes` schedules without modifying solar calculation mathematics:

```text
PrayerTimes (0.4 Frozen Engine)
      ↓
SalahScheduleState (io.github.muslimqol.salah)
      ↓
ReminderEngine (io.github.muslimqol.salah)
      ├── SalahHudOverlay (io.github.muslimqol.client.salah)
      └── SalahToastNotifier (io.github.muslimqol.client.salah)
```

- **Pure Core (`io.github.muslimqol.salah`)**: `SalahEvent`, `SalahScheduleState`, `SalahScheduleService`, `CountdownFormatter`, `ReminderType`, `ReminderKey`, `ReminderDecision`, `SalahNotificationPreferences`, and `ReminderEngine` have **0 Minecraft dependencies, 0 NeoForge dependencies, and 0 network dependencies**.
- **Client Integration (`io.github.muslimqol.client.salah`)**: `SalahClientService`, `SalahHudState`, `SalahHudOverlay`, and `SalahToastNotifier` run strictly on the physical client.

---

## 2. Five Obligatory Prayer Targets

Reminders and next-prayer HUD scheduling target only the five daily obligatory prayers where `Prayer.isObligatoryPrayer()` is `true`:

| Event | Enum | Obligatory (`isObligatoryPrayer`) | Salah HUD & Reminder Target |
| :--- | :--- | :---: | :---: |
| **Fajr** | `Prayer.FAJR` | `true` | Yes |
| **Sunrise** | `Prayer.SUNRISE` | `false` | **No** (astronomical boundary only) |
| **Dhuhr** | `Prayer.DHUHR` | `true` | Yes |
| **Asr** | `Prayer.ASR` | `true` | Yes |
| **Maghrib** | `Prayer.MAGHRIB` | `true` | Yes |
| **Isha** | `Prayer.ISHA` | `true` | Yes |

`Prayer.SUNRISE` remains available internally in `PrayerTimes` as a calculated solar boundary, but is never selected as `nextPrayer`, `previousPrayer`, or a Salah reminder target.

---

## 3. Schedule State & Terminology

`SalahScheduleService` evaluates adjacent daily schedules (`yesterday`, `today`, `tomorrow`) in the configured prayer `ZoneId` against the current `Instant`:

- **`nextPrayer`**: The earliest available obligatory prayer with `instant > evaluatedAt`.
  - After today's `Isha`, `nextPrayer` resolves to tomorrow's first available obligatory prayer (typically tomorrow's `Fajr`).
- **`previousPrayer` (`lastStartedPrayer`)**: The most recently started available obligatory prayer with `instant <= evaluatedAt` (falling back to yesterday's latest available obligatory prayer before today's `Fajr`).

> [!NOTE]
> **Terminology Clarification (`previousPrayer` / `lastStartedPrayer`)**  
> `"previous prayer"` means most recently calculated obligatory prayer start, not a ruling about whether its valid prayer window remains open. MuslimQoL 0.5 does not claim `"You are currently in <Prayer> time"` or model jurisprudential end-of-window boundaries.

---

## 4. Cross-Midnight, Timezone, & High-Latitude Behavior

- **Cross-Midnight Countdowns**: Remaining time is computed strictly via `Duration.between(evaluatedAt, nextPrayer.instant())`, never via `LocalTime` subtraction. A check at `23:50` before tomorrow's `05:10` Fajr yields `5h 20m` without negative or wrapped values.
- **Timezone & DST**: Schedule evaluation reuses the `ZoneId` resolved by Prayer Calculation 0.4 (`LocalDate.ofInstant(now, configuredZoneId)`). Next-prayer local time is formatted in that `ZoneId` as `HH:mm` (24-hour format) and handles Daylight Saving Time transitions automatically through `Instant + ZoneId`.
- **High-Latitude Unavailable Events**: If an obligatory prayer has `PrayerTimeSource.UNAVAILABLE` (e.g., extreme polar conditions with `HighLatitudeRule.NONE`), `SalahScheduleService` and `ReminderEngine` skip the unavailable moment without fabricating fallback timestamps and continue searching for the next available obligatory prayer. If no obligatory prayer is available in the search horizon, `SalahScheduleState` returns an explicit empty `nextPrayer` and the HUD hides cleanly.

---

## 5. Countdown Formatting

`CountdownFormatter` produces stable, seconds-free countdown text:

| Remaining Duration | Formatted Output |
| :--- | :--- |
| `<= 0s` or `< 60s` (`1s..59s`) | `<1m` |
| `1m..59m` (`60s..3599s`) | `1m` .. `59m` |
| `>= 60m` | `1h 00m`, `2h 05m`, `5h 20m`, `24h 00m` |

Negative countdown strings are never produced.

---

## 6. Reminder Engine & Notification Delivery

`ReminderEngine` evaluates two reminder types on a 1-second client tick cadence:

1. **`UPCOMING` (Advance Reminder)**:
   - Triggered at `prayerInstant - Duration.ofMinutes(advance_notification_minutes)`.
   - Controlled by `prayer.advance_notification_enabled` and `prayer.advance_notification_minutes` (`0..60`, default `10`).
   - Setting `advance_notification_minutes = 0` explicitly means **no advance notification**.
   - Example Toast:
     - Title: `Asr`
     - Body: `Prayer in 10 minutes`
2. **`STARTED` (Prayer-Start Reminder)**:
   - Triggered at `prayerInstant`.
   - Controlled by `prayer.start_notification_enabled`.
   - Example Toast:
     - Title: `Asr`
     - Body: `Prayer time has begun`

### Time-Crossing, Catch-Up, & Deduplication Guarantees

- **Time-Crossing Semantics**: Triggers fire when `windowStart < triggerInstant <= currentPollInstant`.
- **No Startup Replay**: On initial client evaluation (or when schedule configuration changes), `ReminderEngine` initializes its poll watermark to `currentPollInstant` and never replays historical reminders from earlier in the day.
- **Forward Clock Jump & Catch-Up Grace (`MAX_CATCH_UP_DURATION = 5 minutes`)**: If the system clock jumps forward (e.g., after sleep/resume), reminders within the past 5 minutes are caught up; stale reminders older than 5 minutes are not replayed.
- **Backward Clock Jump Handling**: If `currentPollInstant < previousPollInstant`, the poll watermark resets safely to `currentPollInstant` while retaining delivered reminder keys so no duplicate notifications fire when time advances again.
- **Session Deduplication**: Every reminder is keyed by `ReminderKey(civilDate, prayer, type, prayerInstant)` in a bounded in-memory set (`MAX_DELIVERED_KEYS = 256`), guaranteeing each reminder fires at most once per session.

---

## 7. HUD & Qibla Coexistence

`SalahHudOverlay` renders a compact two-line indicator in the top-left corner:

```text
Next: Asr 16:37
in 1h 24m
```

- **Deterministic Coexistence with Qibla HUD**:
  - When the Qibla HUD is visible (`y = 8..24`), the Salah HUD renders directly below it at `x = 8, y = 28`.
  - When the Qibla HUD is hidden or disabled, the Salah HUD renders at `x = 8, y = 8`.
- **Visibility Suppression**:
  - Automatically hides when `prayer.enabled = false`, `qibla.location_configured = false`, `prayer.salah_hud_enabled = false`, the prayer schedule is unavailable, GUI is hidden (`F1`), or the debug screen (`F3`) is open.
  - Never displays permanent error banners or raw coordinates on screen.

---

## 8. Client Configuration (`config/muslimqol-client.toml`)

```toml
[prayer]
    # Show next obligatory prayer and countdown on the client HUD
    salah_hud_enabled = true

    # Master switch for client-side Salah reminder Toast notifications
    notifications_enabled = true

    # Enable advance reminder notification before an obligatory prayer begins
    advance_notification_enabled = true

    # Minutes before an obligatory prayer to show the advance reminder (0 to 60; 0 = no advance notification)
    advance_notification_minutes = 10

    # Enable reminder notification when an obligatory prayer start time is reached
    start_notification_enabled = true

    # Per-prayer notification switches (affect Toast notifications only; schedule and HUD remain intact)
    notify_fajr = true
    notify_dhuhr = true
    notify_asr = true
    notify_maghrib = true
    notify_isha = true
```

---

## 9. Privacy, Scope, & Limitations

- **Client-Only Privacy**: Observer coordinates remain strictly in `muslimqol-client.toml`. Salah 0.5 adds **0 network packets, 0 server storage, 0 external HTTP requests, 0 IP lookups, and 0 GPS queries**. Neither notifications nor logs contain observer coordinates.
- **No Adhan Audio**: 0.5 does not bundle or play audio files (`adhan.mp3` / `adhan.ogg`).
- **No Server Broadcast or Gameplay Enforcement**: Reminders are personal client-side Minecraft Toasts only. They never freeze the player, block actions, alter hunger, send chat spam, or trigger OS-level desktop notifications.
