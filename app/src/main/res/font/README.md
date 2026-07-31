# Fonts needed here

`Type.kt` references these font resource files, which are not included in
this deliverable because font binaries shouldn't be generated — download
the real `.ttf` files instead (both are open-license Google Fonts, free for
commercial use):

**JetBrains Mono** — https://fonts.google.com/specimen/JetBrains+Mono
Download and place as:
- `jetbrains_mono_regular.ttf`
- `jetbrains_mono_medium.ttf`
- `jetbrains_mono_semibold.ttf`
- `jetbrains_mono_bold.ttf`

**Inter** — https://fonts.google.com/specimen/Inter
Download and place as:
- `inter_regular.ttf`
- `inter_medium.ttf`
- `inter_semibold.ttf`
- `inter_bold.ttf`

## Fastest way to add them

In Android Studio: right-click `res` → New → Font Resource, or simply drag
the downloaded `.ttf` files into `app/src/main/res/font/` — Android Studio
resource filename validation will confirm they're named correctly (must be
all-lowercase with underscores, matching the names above exactly).

## If you'd rather not manage font files at all

Replace the `Font(R.font....)` calls in `Type.kt` with
`androidx.compose.ui.text.googlefonts.GoogleFont` + a `Provider`, which
downloads these same two families at runtime via the Google Fonts provider
API instead of bundling them. That needs one extra dependency
(`androidx.compose.ui:ui-text-google-fonts`) — not included by default here
to keep the app fully offline-capable with zero runtime font fetches.
