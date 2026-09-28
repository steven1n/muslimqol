# Qibla Direction Feature

MuslimQoL 0.3 introduces a mathematically rigorous, offline, privacy-preserving Qibla direction indicator for Minecraft players.

---

## 1. Overview

MuslimQoL calculates the initial great-circle bearing from a player's real-world observer coordinates to the Kaaba in Mecca, projecting that direction into the Minecraft world using the project's explicit coordinate orientation convention.

### Key Principles

- **Offline & Local**: Pure mathematical calculation using `java.lang.Math` trigonometry.
- **Client-Side Privacy**: Real-world observer coordinates remain strictly on the local client and are never transmitted to multiplayer servers, stored in server-side configurations, or queried via external network APIs.
- **Minimal HUD**: Unobtrusive on-screen direction indicator and relative compass strip with zero external image asset dependencies.

---

## 2. World-Orientation Convention

Minecraft worlds have no intrinsic Earth geographic orientation. MuslimQoL defines the following explicit convention:

```text
Minecraft North (-Z) = Geographic North (0°)
Minecraft East  (+X) = Geographic East  (90°)
Minecraft South (+Z) = Geographic South (180°)
Minecraft West  (-X) = Geographic West  (270°)
```

> [!IMPORTANT]
> The Qibla HUD indicates the direction within the Minecraft world that corresponds to the real-world Qibla bearing under the convention that **Minecraft North (-Z) represents Geographic North (0°)**.

---

## 3. Mathematical Model

### Reference Coordinates

Kaaba reference coordinates in Mecca are defined as centralized constants:

- **Latitude**: `21.4225° N`
- **Longitude**: `39.8262° E`

### Great-Circle Initial Bearing Formula

Given observer coordinates $(\phi_1, \lambda_1)$ and Kaaba coordinates $(\phi_2, \lambda_2)$ in radians, with difference in longitude $\Delta\lambda = \lambda_2 - \lambda_1$:

$$\Delta\lambda = \lambda_2 - \lambda_1$$

$$y = \sin(\Delta\lambda) \cdot \cos(\phi_2)$$

$$x = \cos(\phi_1) \cdot \sin(\phi_2) - \sin(\phi_1) \cdot \cos(\phi_2) \cdot \cos(\Delta\lambda)$$

$$\theta = \operatorname{atan2}(y, x)$$

$$\text{bearing} = (\operatorname{degrees}(\theta) + 360) \pmod{360}$$

Bearings are normalized to $[0.0^\circ, 360.0^\circ)$ where $0^\circ = \text{North}$, $90^\circ = \text{East}$, $180^\circ = \text{South}$, and $270^\circ = \text{West}$.

### Degenerate Cases

- **Observer at Kaaba (`AT_KAABA`)**: If observer coordinates match the Kaaba within numerical epsilon, bearing is undefined.
- **Antipodal Point (`ANTIPODAL`)**: If observer coordinates are antipodal to the Kaaba ($-\phi_2, \lambda_2 \pm 180^\circ$), all great circles are equidistant ($\pi$ radians) and initial bearing is indeterminate.
- In both degenerate cases, `QiblaResult.defined()` returns `false` with bearing `Double.NaN`, preventing invalid or arbitrary directions from rendering.

---

## 4. Minecraft Player Yaw & Relative Angle

Minecraft player yaw convention:

- `yaw 0°` = South (+Z)
- `yaw 90°` = West (-X)
- `yaw -90°` = East (+X)
- `yaw ±180°` = North (-Z)

Player heading in geographic degrees:

$$\text{headingDeg} = \operatorname{normalize360}(180.0 + \text{playerYawDeg})$$

Relative Qibla angle from player viewpoint:

$$\text{relativeAngleDeg} = \operatorname{normalizeSigned}(\text{qiblaBearingDeg} - \text{playerHeadingDeg})$$

Convention:

- `0°`: Player directly faces Qibla.
- `+1° to +179°`: Qibla is to the player's right.
- `-1° to -179°`: Qibla is to the player's left.
- `±180°`: Qibla is directly behind the player.

### Alignment Tolerance

When $|\text{relativeAngleDeg}| \le 3.0^\circ$, the player is considered **Aligned** (`hud.muslimqol.qibla.aligned`).

---

## 5. Client Privacy Model

Observer location is sensitive personal information. MuslimQoL enforces strict client-side isolation:

- **Client Configuration Only**: Latitude and longitude are stored exclusively in `config/muslimqol-client.toml`.
- **No Network Transmission**: Configured coordinates are never sent across the network, synced via mod packets, or visible to server administrators.
- **No External Network Calls**: Zero IP geolocation, zero GPS, zero Wi-Fi triangulation, and zero web API requests.
- **No Leakage**: Coordinates are not logged or exposed in common/server configurations.

---

## 6. Configuration

Configure observer coordinates in `config/muslimqol-client.toml`:

```toml
[qibla]
    # Master switch for Qibla features
    enabled = true

    # Explicit flag indicating observer coordinates have been manually set
    location_configured = true

    # Observer latitude in decimal degrees (-90.0 to +90.0). Positive = North, Negative = South.
    latitude = 51.5074

    # Observer longitude in decimal degrees (-180.0 to +180.0). Positive = East, Negative = West.
    longitude = -0.1278

    # Show Qibla direction indicator on the HUD
    hud_enabled = true
```

> [!NOTE]
> Setting `latitude = 0.0` and `longitude = 0.0` does not enable the indicator until `location_configured = true` is explicitly set, preventing accidental display for unconfigured clients.

---

## 7. HUD Indicator Behavior

- **Unconfigured State**: When `location_configured` is `false`, the HUD indicator remains completely hidden. No unwanted placeholder text is displayed.
- **Configured & Enabled**:
  - Displays localized text (e.g., `Qibla 119.0°` in English or `القبلة 119.0°` in Arabic).
  - When aligned within $\pm 3.0^\circ$, displays `Qibla 119.0° | Aligned` in green with a centered green pip.
  - When Qibla is to the right, the compass strip indicator smoothly shifts right.
  - When Qibla is to the left, the compass strip indicator smoothly shifts left.
  - When Qibla is behind ($|\text{relativeAngle}| > 135^\circ$), edge warning markers appear in amber.

---

## 8. Limitations & Scope

MuslimQoL Qibla calculation is a mathematical initial great-circle bearing from manually entered geographic coordinates under the Minecraft North (-Z) convention. It is not a real-world sensor, GPS device, or certified religious instrument.

---

## 9. Verification & QA Status (0.3 Freeze)

- **Automated Tests**: `AUTOMATED PASS`
- **Client Boot**: `CLIENT BOOT PASS`
- **Dedicated Server Runtime**: `DEDICATED SERVER RUNTIME PASS`
- **Manual HUD QA**: `MANUAL PASS`
  - Absolute bearing display: `PASS`
  - Aligned state: `PASS`
  - Centered aligned marker: `PASS`
  - Qibla right indication: `PASS`
  - Qibla left indication: `PASS`
  - Behind state: `PASS`
  - Directional sign convention: `PASS`
  - HUD visual rendering: `PASS`
  - F3/debug HUD suppression: `PASS`
  - `location_configured=false` hides HUD: `PASS`
  - `hud_enabled=false` hides HUD: `PASS`

