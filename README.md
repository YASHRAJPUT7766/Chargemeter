# ChargeMeter Pro

A real-time charging and battery analyzer for Android. Built with Kotlin,
Jetpack Compose, Room, Hilt, WorkManager, and Glance.

Developed by Yash (yash92726@gmail.com).

---

## Opening this project

1. Open this folder (`ChargeMeterPro/`) directly in Android Studio
   (Ladybug or newer recommended) via **File → Open**.
2. Let Gradle sync. First sync will download dependencies — needs network
   access.
3. Before the first build succeeds, do the required step below (**Fonts**).
4. Run on a physical device or emulator with API 26+ (Android 8.0+).
   Charging-related features are far more meaningful on a physical
   device, since most emulators simulate a generic/static battery state.

---

## Required before first build: Fonts

`ui/theme/Type.kt` references JetBrains Mono and Inter font files that
are **not included** in this deliverable — generating font binaries
isn't something that should be fabricated, so real font files need to be
downloaded instead. Full instructions, exact filenames, and both
required font families (Google Fonts, free/open license) are documented
in:

```
app/src/main/res/font/README.md
```

Takes about 2 minutes: download both families, drop the `.ttf` files
into `app/src/main/res/font/` with the exact names listed there.

---

## Architecture

```
data/
  battery/        Real Android battery API access (BatteryManager, broadcasts)
  local/          Room database (sessions, samples, drain) + DataStore settings
  repository/     Orchestration layer between data sources and the rest of the app
domain/
  model/          BatterySnapshot and friends — the AvailableOr<T> wrapper lives here
  usecase/        All calculations: power, speed classification, time estimates,
                  health scoring, capacity estimation — pure functions, unit-testable
service/          Foreground service (Always-On Monitor), notifications, boot receiver
widget/           Home screen widget (Glance)
export/           CSV and PDF report builders
ui/
  theme/          The "precision instrument panel" design system (colors, type, gauge)
  navigation/     NavHost + bottom nav
  screens/        One package per screen, each with its own ViewModel
  components/     Shared composables (InstrumentCard, ReadingRow, WattMeterGauge, etc.)
di/               Hilt modules
util/             WorkManager workers/schedulers
```

### The core design principle

Every battery/charging value in this app traces back to a real, documented
Android API call — see the doc comments in `data/battery/BatteryDataSource.kt`
for exactly which API backs each field, and which Android version gates it.
Where a device genuinely doesn't expose something (cycle count below API 34,
design capacity on most devices, any specific fast-charge protocol name),
the field is `AvailableOr.Unavailable` and the UI is required to render
"Not available on this device" — nothing in this codebase invents a number
to fill a gap. `domain/usecase/PowerTerminology.kt` centralizes the exact
disclaimer wording used everywhere wattage, efficiency, or health-score
figures are shown, so that language can't drift between screens.

---

## Known items to verify once open in Android Studio

I want to be direct about where this project's confidence is lower, rather
than let you discover it by surprise:

### 1. Vico charts (`ui/components/LiveLineChart.kt`)
Targets Vico `2.0.0-beta.3`. Vico's Compose API has moved meaningfully
across its beta cycle. The file has an in-code comment flagging exactly
which calls to double-check against
[Vico's migration guide](https://patrykandpatrick.com/vico/guide) if
Android Studio reports an unresolved reference. If it's faster to unblock
a build than chase a beta API, swapping to MPAndroidChart (mature, stable
1.0 API, less Compose-idiomatic) is a reasonable fallback — the call sites
are isolated to that one file.

### 2. Glance widget (`widget/ChargeMeterWidget.kt`)
Targets Glance `1.1.1`. Same caveat as Vico — Glance's AppWidget API is
younger than most Jetpack libraries. The file has an in-code note on the
two spots most likely to need an import fix (`actionStartActivity`'s
package, `ColorProvider`'s package). Google's own
[AppWidget samples](https://github.com/android/user-interface-samples/tree/main/AppWidget)
are the fastest reference if either doesn't resolve.

### 3. Everything else
Was written against APIs with high confidence in correctness (core
Compose, Material3 1.3.1, Room, Hilt, WorkManager, DataStore, iText7,
standard Android platform APIs). A static import-checker
(`check_imports.py`, included in the project root — safe to delete, it's
a dev tool, not part of the app) ran throughout development, and every
`@drawable/@layout/@xml/@color/@string/@style` reference across the whole
project was manually verified to resolve to an actual definition. That
process caught and fixed several real issues before they'd ever reach
you, including:
- A missing `PdfWriter` constructor overload (it takes a `String` path
  or `OutputStream`, not a `File` directly — fixed in `PdfExportBuilder.kt`)
- A duplicate/ambiguous import in the widget file
- Several missing `Modifier.weight()`/`.height()`/`.padding()` imports
  that would have been real compile errors
- Two widget XML resources (`widget_preview`, `widget_loading`) that were
  referenced but hadn't been created yet

None of this replaces an actual Gradle build + compile, which is
categorically more reliable than any static check can be — so still
expect to fix a handful of small things on first sync, most likely
confined to the two beta-library files flagged above.

---

## Optional: Firebase setup

Firebase is **not wired in by default** — the project builds and runs
fully without it. Per the product's privacy requirements, Firebase should
only power genuinely optional features (auth, cloud backup, analytics
with consent, crash reporting), never anything core.

To add it later:
1. Create a Firebase project, add an Android app with package name
   `com.yash.chargemeterpro`, download `google-services.json` into
   `app/`.
2. Uncomment the `id("com.google.gms.google-services")` line in
   `app/build.gradle.kts` (top plugins block) and the Firebase
   dependency block further down the same file.
3. Add `<uses-permission android:name="android.permission.INTERNET" />`
   to `AndroidManifest.xml` — deliberately absent right now since the app
   has no network calls without Firebase.
4. Gate any Firebase call behind `SettingsDataStore.cloudBackupEnabled`
   or `.analyticsConsent` (both default `false`) — never call Firebase
   unconditionally.

---

## Optional: Release signing

`app/build.gradle.kts`'s release `signingConfig` reads from environment
variables (`CMP_KEYSTORE_PATH`, `CMP_KEYSTORE_PASSWORD`, `CMP_KEY_ALIAS`,
`CMP_KEY_PASSWORD`) rather than hardcoded secrets. Without them set, a
release build still assembles (debug-signed) so a clean checkout never
fails to build — just isn't installable as an update over a properly
signed release. Generate a keystore via Android Studio's
**Build → Generate Signed App Bundle/APK** wizard, then set those four
environment variables (e.g. in `~/.gradle/gradle.properties`, NOT
committed to version control) before a real release build.

---

## Privacy

All charging history and settings are stored locally on-device (Room +
DataStore) by default. No network permission is requested, and no data
leaves the device unless the user explicitly enables Cloud Backup or
Analytics Consent in Settings (both off by default), or explicitly shares
an exported CSV/PDF via the system share sheet. See the in-app Privacy
Policy screen (`ui/screens/about/AboutScreen.kt` → `PrivacyPolicyScreen`)
for the full user-facing statement.

---

## License / attribution

This is a from-scratch project scaffold. Third-party libraries retain
their own licenses (Vico, Glance, iText7, Accompanist, AndroidX, etc. —
see their respective repositories). iText7 core is AGPL-licensed for free
use; a commercial iText license is required if this app is distributed
under closed-source terms without complying with AGPL's source-availability
requirements — worth checking before a commercial release.
