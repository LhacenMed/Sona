# ArchiveTune Dynamic Material Theming — Technical Research

Source project: `C:\Users\lhacenmed\AndroidStudioProjects\ArchiveTune`
Package root: `moe.rukamori.archivetune`
Relevant files (all under `app/src/main/kotlin/moe/rukamori/archivetune/`):
- `ui/theme/Theme.kt` — core theme composable, color-scheme construction, animation, persistence codec
- `ui/theme/PlayerColorExtractor.kt` — gradient/mesh color extraction from Palette for the player UI
- `ui/theme/PlayerBackgroundColorUtils.kt` — HSV comfort clamps + gradient stop builders
- `ui/theme/palette/TonalPalettes.kt` — HCT tonal-palette map builder (uses materialkolor's Hct)
- `ui/theme/PlayerSliderColors.kt` — cosmetic, derives slider colors from a single "active" color
- `ui/screens/settings/AppearanceSettings.kt` — settings UI (toggles)
- `ui/screens/settings/PalettePickerScreen.kt` — preset seed-palette picker (`ThemePalette`, `ThemePalettes` object)
- `constants/PreferenceKeys.kt` — DataStore Preferences keys
- `MainActivity.kt` (lines ~738-850) — wiring: DataStore → LaunchedEffect → Coil image decode → Palette extraction → `themeColor` state → `ArchiveTuneTheme`

Gradle deps (`app/build.gradle.kts` / `gradle/libs.versions.toml`):
- `androidx.palette:palette-ktx:1.0.0` (`libs.palette`) — Android Palette API, real bitmap→swatch extraction
- `com.materialkolor:material-kolor:5.0.0-alpha07` — direct string coordinate (NOT in version catalog), wraps Google's `material-color-utilities` (HCT color space) and exposes `dynamicColorScheme(seedColor, isDark, contrastLevel, style)`, `PaletteStyle`, `toHct()`/`toColor()` extensions on Compose `Color`/`Hct`
- `io.coil-kt.coil3:coil-compose` (v3.5.0) — async image loading, used to fetch/decode the current track's artwork bitmap
- No use of `androidx.compose.material3:material3` system dynamic color beyond the stock `dynamicDarkColorScheme`/`dynamicLightColorScheme` (Android 12+ wallpaper Monet), used only as an alternate path, not combined with materialkolor.

---

## 1. Dynamic theme color extraction from album art

**Library**: Android's own `androidx.palette.graphics.Palette` (statistical k-means-ish quantization + swatch scoring, NOT Material You/Monet — Monet/HCT only enters later, in step 2, applied to the *seed color* Palette hands back).

### Pipeline (MainActivity.kt, `LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor)`, ~line 804)

1. Effect keys on `playerConnection`, `enableDynamicTheme` (user setting), `isSystemInDarkTheme`, and `customThemeColor` (the manual fallback color). If dynamic theme is disabled or there's no player connection yet, `themeColor` is immediately set to either `customThemeColor` or `DefaultThemeColor` and the effect returns — no extraction runs.
2. Otherwise it does `playerConnection.service.currentMediaMetadata.collectLatest { song -> ... }` — a Flow from the media session/service exposing the currently playing track's metadata. `collectLatest` means: **a new track cancels any in-flight extraction for the previous track** (natural debounce/cancellation, no explicit `debounce()` operator).
3. For a non-null `song`, on `Dispatchers.Default`:
   - Loads artwork via Coil: `imageLoader.execute(ImageRequest.Builder(context).data(song.thumbnailUrl).allowHardware(false).build())`. `allowHardware(false)` is required because `Palette` needs to read raw pixels off a software bitmap (HARDWARE bitmaps can't be inspected pixel-by-pixel).
   - `result.image?.toBitmap()?.extractThemeColor()` — calls the extension function in `Theme.kt`:
     ```kotlin
     fun Bitmap.extractThemeColor(): Color {
         val palette = Palette.from(this).maximumColorCount(16).generate()
         val swatch = palette.vibrantSwatch
             ?: palette.dominantSwatch
             ?: palette.mutedSwatch
             ?: palette.lightVibrantSwatch
             ?: palette.darkVibrantSwatch
             ?: palette.lightMutedSwatch
             ?: palette.darkMutedSwatch
         return swatch?.rgb?.toComposeColor() ?: DefaultThemeColor
     }
     ```
     Priority order: vibrant → dominant → muted → light-vibrant → dark-vibrant → light-muted → dark-muted → hardcoded `DefaultThemeColor` (`0xFFED5564`, a pink/red).
   - Result posted back to `Dispatchers.Main`, assigning `themeColor = extractedColor ?: DefaultThemeColor`.
   - Any exception during load/extraction is caught and `themeColor` falls back to `DefaultThemeColor`.
4. If `song == null` (playback stopped/queue empty): on API 31+ (`Build.VERSION_CODES.S`) reset to `DefaultThemeColor` (so it falls through to system dynamic color, see §3); below API 31, fall back to the user's manual `customThemeColor`.

**Re-run cadence**: exactly once per track change (driven by the media session's `currentMediaMetadata` flow), not time-debounced, not cached to disk — recomputed from network/cache image each time via Coil (Coil's own memory/disk cache avoids re-downloading, but Palette re-runs every time since Palette objects aren't cached across tracks). No separate "if same track skip" check beyond the Flow only emitting on actual metadata changes.

### Secondary richer extractor for player-screen gradients/backdrops: `PlayerColorExtractor` (object)

Used only inside the Player UI (`ui/player/Player.kt`) for building multi-color mesh/gradient backdrops behind the full-screen player (distinct from the single `themeColor` used for the whole app's Material scheme). Not used for the app-wide ColorScheme.

- `Player.kt` builds its own `Palette` per artwork bitmap: `Palette.from(bitmap).maximumColorCount(Config.MAX_COLOR_COUNT /*32*/).resizeBitmapArea(Config.BITMAP_AREA /*8000*/).generate()`, with the bitmap pre-scaled to `Config.IMAGE_SIZE` (128x128) before palette generation (perf: small bitmap, small pixel area).
- `PlayerColorExtractor.extractGradientColors(palette, fallbackColor): List<Color>` (suspend, runs on `Dispatchers.Default`):
  1. Collects up to 7 candidate swatches (vibrant, lightVibrant, darkVibrant, dominant, muted, darkMuted, lightMuted), dedupes by rgb.
  2. Ranks them by `calculateColorWeight` = `population * (1.3 if saturation>0.3 && brightness in [0.2,0.9] else 1.0)`.
  3. Greedily keeps up to 6 "unique" colors (`isSimilarToAny` rejects colors within hue<12°, sat<0.12, val<0.12 OR RGB channel diff <28), each boosted via `enhanceColorVividness` (saturation ×1.25 or ×1.05 depending on how saturated it already is; value ×1.02 clamped to [0.32, 0.88]).
  4. Computes a population-weighted average saturation across all swatches; if `<0.22` (or the dominant picked color is itself near-gray via `isNearGray`: sat<0.15 or val<0.08) — treats the artwork as **greyscale** and instead synthesizes 6 grayscale stops from the dominant swatch's brightness (`baseBrightness`), via fixed multipliers (1.2, 0.9, 0.6, 1.4, 0.7, 0.5) each clamped into its own range, converted with `HSVToColor(0,0,v)`.
  5. Otherwise (colorful image), if fewer than 6 colors were found, synthesizes more by hue-shifting existing colors by a fixed rotation sequence `[25,-25,55,-55,120,-120,180,150,-150]°` off a rotating base candidate, then "tuning" each (`tuneColorForMesh`: clamp saturation ≥0.62 then ×1.08; blend value 85%/15% toward a per-slot target from `[0.82,0.74,0.68,0.6,0.86,0.7]`, clamp to [0.38,0.9]) until 6 unique colors exist or 40 attempts exhausted.
  6. Returns a `List<Color>` (typically 6 entries): index 0 is the true dominant/extracted color; indices 1+ are synthetic hue-shifted mesh variants — a later consumer (`V7BackdropPalette`) explicitly only trusts `colors[0]` for hue and derives darker tones from it rather than using the synthetic ones, because they intentionally rotate hue and would look "wrong" for a coherent single-hue backdrop.
- Consumers: `Player.kt`'s `V7BackdropPalette.fromColors(colors, fallbackColor)` builds a 3-stop (top/mid/bottom) backdrop purely from `colors.firstOrNull()`, adjusting brightness via `v7BackdropTone(valueMin, valueMax)` per band; falls back to `Color(fallbackColor)` toned the same way if extraction failed/empty.
- `PlayerBackgroundColorUtils` (separate utility) provides `ensureComfortableColor` (clamps saturation ≥0.32, value into [0.15,0.58]) and gradient-stop builders (`buildColoringStops`, `buildBlurOverlayStops`, `buildBlurGradientStops`) used elsewhere (mini player / lyrics / AOD screens) to keep extracted colors from being too dark/desaturated/too bright for text contrast.
- There's also a simpler top-level `Bitmap.extractGradientColors(): List<Color>` in `Theme.kt` (separate, simpler algorithm — picks top-2 swatches by population maximizing hue/sat/value distance, sorted by luminance) used for lighter-weight 2-color gradients (e.g. library/queue item art) — not the same as `PlayerColorExtractor`.

---

## 2. Theme application / propagation → Material3 ColorScheme

### Seed → full ColorScheme construction (`Theme.kt`)

The single extracted `themeColor` (a `Color`, i.e. one RGB "seed") is turned into a **materialkolor** HCT-based scheme, not `androidx.palette`/manual tonal math directly (except `TonalPalettes.kt`, which is an alternate/unused-by-default helper producing per-tone maps via `Hct.from(...).withTone(...)`, likely for custom UI needing raw tonal stops rather than the M3 role names).

Core function `materialKolorDynamicColorScheme(keyColor, isDark, contrastLevel=0.0, style)`:
- Picks a `PaletteStyle` (`com.materialkolor.PaletteStyle`) based on the seed's HCT chroma (`seedColor.toHct().chroma`):
  - chroma < 4.0 → `PaletteStyle.Monochrome`
  - chroma < 12.0 → `PaletteStyle.Neutral`
  - else → `PaletteStyle.TonalSpot` (the standard Material You algorithm)
- Calls `mergedSeedColorScheme(primarySeed=keyColor, secondarySeed=keyColor, tertiarySeed=keyColor, neutralSeed=keyColor, isDark, contrastLevel, style)`.
- `mergedSeedColorScheme` independently generates FOUR full material-kolor `ColorScheme`s (`dynamicColorScheme(seedColor, isDark, contrastLevel, style)` from the `com.materialkolor` library, one per seed — primary/secondary/tertiary/neutral, each with its own `paletteStyleFor` chroma-based style pick) and then hand-picks specific roles from each into ONE final `androidx.compose.material3.ColorScheme`:
  - primary/onPrimary/primaryContainer/onPrimaryContainer/inversePrimary/surfaceTint/error*/scrim ← from the **primary** sub-scheme (error/scrim taken from primary scheme too)
  - secondary/onSecondary/secondaryContainer/onSecondaryContainer ← from the **secondary** sub-scheme's primary-role slots
  - tertiary/onTertiary/tertiaryContainer/onTertiaryContainer ← from the **tertiary** sub-scheme's primary-role slots
  - background/surface*/outline*/inverseSurface*/scrim ← from the **neutral** sub-scheme
  - When there's only one real seed (the common single-`themeColor` case from album art), all four sub-schemes are generated from the *same* color, so this reduces to a normal single-seed Material You scheme; the 4-seed merge machinery exists for the **manual "seed palette" theme picker** path (§4) where a user can pick 4 different swatches (primary/secondary/tertiary/neutral) and the app blends them into one coherent scheme.
- `exactPaletteColorScheme(palette: ThemeSeedPalette, isDark)` is the same `mergedSeedColorScheme` call but seeded with 4 distinct user-chosen colors instead of 1 repeated color — used when the user picked a manual theme (`enableDynamicTheme == false` and a `ThemeSeedPalette` is set).

### `ArchiveTuneTheme` composable (Theme.kt, ~line 72)

Signature:
```kotlin
@Composable
fun ArchiveTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    seedPalette: ThemeSeedPalette? = null,
    disableAnimations: Boolean = false,
    fontPreference: AppFontPreference = AppFontPreference.DEFAULT,
    customFontUri: String = "",
    content: @Composable () -> Unit,
)
```
Logic:
1. `useSystemDynamicColor = (seedPalette == null && themeColor == DefaultThemeColor && SDK_INT >= S)` — i.e. system Monet (Android 12+ wallpaper colors) is used ONLY when there is no manual seed palette AND no extracted/custom theme color is active (still at the literal default). In practice this triggers when dynamic theming from album art is on but no track is playing yet / extraction reset to default, or theming is fully off with default settings.
2. Font resolution: `produceState` async-loads a custom font file (`CustomFontLoader.loadFontFamily`) if `fontPreference == CUSTOM`; otherwise picks between `AppFontFamily` (default) / `FontFamily.Default` (system) / custom.
3. `motionScheme = if (disableAnimations) DisabledMotionScheme else MotionScheme.expressive()` — a custom `MotionScheme` implementation whose 6 spec functions all return `snap()` when animations are disabled (used for low-RAM devices).
4. `appColorScheme` = `remember(seedPalette, themeColor, darkTheme)`: `exactPaletteColorScheme(...)` if a manual `seedPalette` is set, else `materialKolorDynamicColorScheme(themeColor, ...)`.
5. `baseColorScheme` = system `dynamicDarkColorScheme(context)`/`dynamicLightColorScheme(context)` (real androidx Material3 API, Android 12+) if `useSystemDynamicColor`, else `appColorScheme`.
6. `colorScheme` = `baseColorScheme.pureBlack(true)` if `darkTheme && pureBlack`, forcing `surface` and `background` to pure `Color.Black` (AMOLED mode) — everything else in the scheme untouched.
7. **Animation**: if `!disableAnimations`, wraps the final scheme through `animateColorScheme(targetColorScheme, motionScheme.defaultEffectsSpec())` — a hand-written function that calls `animateColorAsState(...)` individually on all ~35 `ColorScheme` fields (primary, onPrimary, containers, surfaces, outline, scrim, etc.) using the same `FiniteAnimationSpec<Color>` from the motion scheme, and reassembles a new `ColorScheme` from the animated `State<Color>` values. This is what makes theme color changes crossfade smoothly rather than snap when a new track's color kicks in. If `disableAnimations`, the raw target scheme is used unanimated.
8. Wraps everything with `CompositionLocalProvider(LocalArchiveTuneFontPreference, LocalArchiveTuneFontFamily)` then `MaterialExpressiveTheme(colorScheme = animatedColorScheme, motionScheme, typography, shapes = expressiveShapes, content)` — `MaterialExpressiveTheme` is the Material3 **Expressive** variant of `MaterialTheme` (experimental M3 API) that also carries `motionScheme` through composition, in addition to the usual `colorScheme`/`typography`/`shapes`.
9. Custom `Shapes` object with fixed rounded-corner radii: extraSmall 8dp, small 12dp, medium 16dp, large 24dp, extraLarge 32dp.

### Propagation mechanism
- **No custom CompositionLocal for the ColorScheme itself** — it rides on Compose Material3's own internal `LocalColorScheme` via `MaterialExpressiveTheme`/`MaterialTheme`, so any descendant reads it the normal way: `MaterialTheme.colorScheme.primary`, etc.
- State ownership: `themeColor` is a `rememberSaveable` `MutableState<Color>` living in `MainActivity`'s Compose entry point (not a ViewModel/StateFlow) — it's mutated by the `LaunchedEffect` collecting the player's `currentMediaMetadata` Flow, and passed as a plain parameter into `ArchiveTuneTheme`. So the "pipeline" is: MediaSession Flow → LaunchedEffect side-effect → local `mutableStateOf<Color>` → recomposition of `ArchiveTuneTheme` → new `ColorScheme` (memoized via `remember(seedPalette, themeColor, darkTheme)`) → animated via `animateColorAsState` per-field → `MaterialExpressiveTheme` → whole app tree.
- Custom fonts and font-preference propagate via two `staticCompositionLocalOf`: `LocalArchiveTuneFontPreference`, `LocalArchiveTuneFontFamily`.

---

## 3. Fallback behavior

- **No artwork / metadata null**: `themeColor` resets to `DefaultThemeColor` (`0xFFED5564`) on API 31+ (which then triggers `useSystemDynamicColor` inside `ArchiveTuneTheme`, i.e. falls through to Android's wallpaper-based Monet if available), or to the user's manually configured `customThemeColor` below API 31 (since system dynamic color isn't available pre-S).
- **Extraction throws / Coil load fails**: caught in a try/catch around the Coil `imageLoader.execute` + `extractThemeColor()` call; `themeColor` set to `DefaultThemeColor`, same fallthrough as above.
- **Palette has no usable swatches** (`extractThemeColor`): the `?:` chain falls through vibrant→dominant→muted→light-vibrant→dark-vibrant→light-muted→dark-muted, and if literally every swatch is null, returns `DefaultThemeColor` directly (never a crash/null).
- **Dynamic theming toggled off entirely** (`enableDynamicTheme == false`): the `LaunchedEffect` short-circuits immediately (no image work is even attempted) and `themeColor = customThemeColor` (the user's manual pick from settings, itself defaulting to `DefaultThemeColor` if the user never customized it).
- **`PlayerColorExtractor.extractGradientColors`**: guards against empty candidate swatch lists (`availableColors.isEmpty()` → adds one derived from `fallbackColor`), and against greyscale/near-gray dominant colors via `isNearGray` (falls back to a fixed gray-stop ramp rather than trying to force saturation onto a genuinely gray image).
- **`Bitmap.extractGradientColors()`** (Theme.kt's simpler 2-color variant): if `palette.swatches` is completely empty, returns a hardcoded 2-stop dark gray gradient `[Color(0xFF595959), Color(0xFF0D0D0D)]`.

---

## 4. Persistence & settings

All persisted via Jetpack DataStore `Preferences` (keys in `constants/PreferenceKeys.kt`), read/written with a `rememberPreference`/`rememberEnumPreference` delegate pattern (`by rememberPreference(Key, defaultValue)` gives a `MutableState`-like property that syncs to DataStore).

Keys (all in `moe.rukamori.archivetune.constants`, `PreferenceKeys.kt`):
- `DynamicThemeKey = booleanPreferencesKey("dynamicTheme")` — default `true`. Master toggle for "extract color from album art." Exposed in Settings → Appearance (`AppearanceSettings.kt` line ~152, a switch bound to `onDynamicThemeChange`).
- `CustomThemeColorKey = stringPreferencesKey("customThemeColor")` — default `"default"`. Stores the manual theme selection used when `DynamicThemeKey` is off (or as the pre-S / no-track fallback). Its string encoding is polymorphic, decoded in `MainActivity.kt` (~line 757):
  - starts with `"#"` → a literal hex color string (single-color manual theme, parsed via `android.graphics.Color.parseColor`).
  - starts with `"seedPalette:"` → a base64+JSON-encoded 4-color `ThemeSeedPalette` (primary/secondary/tertiary/neutral), decoded via `ThemeSeedPaletteCodec.decodeFromPreference` (Theme.kt). The codec's payload is `ThemeExportV1(version=1, name?, primary, secondary, tertiary, neutral)` (hex ARGB strings) serialized with kotlinx.serialization `Json`, base64url-encoded, prefixed `"seedPalette:"`. Also supports decoding a bare/legacy JSON object (no prefix, `decodeFromLegacyObject`) for imported theme files — i.e. users can export/import theme JSON (`ThemeSeedPaletteCodec.encodeAsJson`/`decodeFromJson`), presumably via a share/import flow in `PalettePickerScreen.kt`.
  - otherwise → treated as a preset palette ID, looked up via `ThemePalettes.findById(id)` (in `ui/screens/settings/PalettePickerScreen.kt`, a hardcoded object exposing `allPalettes: List<ThemePalette>` and `findById(id): ThemePalette?`), then converted to a `ThemeSeedPalette`.
- `RandomThemeOnStartupKey = booleanPreferencesKey("randomThemeOnStartup")` — presumably picks a random preset `ThemePalette` at app launch (referenced in prefs but wiring beyond the key itself wasn't traced further; the flag exists in `PreferenceKeys.kt`).
- `DarkModeKey = stringPreferencesKey("darkMode")` with enum `DarkMode { ON, OFF, AUTO }`, default `AUTO` (follows `isSystemInDarkTheme()`).
- `PureBlackKey = booleanPreferencesKey("pureBlack")` — default `false`; only takes effect when dark theme is active (`pureBlack = pureBlackEnabled && useDarkTheme`), forces `surface`/`background` to true black for AMOLED.
- `DisableAnimationsKey = booleanPreferencesKey("disableAnimations")` — default derived from `applicationContext.isLowRamDevice()` (auto-on for low-RAM devices); disables both the custom `MotionScheme` (all specs → `snap()`) and the per-field `animateColorAsState` crossfade in `animateColorScheme`.
- `ForceHighRefreshRateKey`, `UseSystemFontKey` (legacy, migrated to `FontPreferenceKey` on first read if unset), `FontPreferenceKey` (`AppFontPreference` enum: DEFAULT/SYSTEM/CUSTOM), `CustomFontUriKey`, `CustomFontNameKey` — font-related, not color, but flow through the same `ArchiveTuneTheme` composable parameters.

**Interaction with system dynamic color (Android 12+ Monet)**: There is no setting to force system dynamic color independent of the album-art feature. It's implicit fallback logic only: `useSystemDynamicColor` is true exactly when (a) no manual seed palette is active, (b) `themeColor` is still literally `DefaultThemeColor` (i.e., either dynamic-theme-from-art is on but nothing has been extracted yet / no song playing, or the app is in some default state), and (c) `SDK_INT >= S`. So system Monet acts purely as the "nothing else to show" wallpaper-derived backdrop rather than a user-toggleable competing mode; album art extraction (when a song is playing) and manual palettes both take priority over it.

**No ViewModel/Repository layer for theming** — everything lives in Activity-scoped Compose state (`rememberSaveable`, `rememberPreference`) plus the two theme utility files; the "persistence" layer is exclusively DataStore Preferences string/boolean keys, not a database table or dedicated ThemeRepository class.

---

## Summary of exact reimplementation recipe for Sona (Compose, package com.lhacenmed.sona)

1. Add deps: `androidx.palette:palette-ktx` (extraction) and `com.materialkolor:material-kolor` (HCT scheme generation) — no need for Coil specifically, but need decodable `Bitmap` access to the current track's artwork, decoded as a software (non-hardware) bitmap.
2. On each track change (collect the media-session/player's "current metadata" Flow with `collectLatest` for automatic cancellation-based debouncing), decode artwork bitmap → `Palette.from(bitmap).maximumColorCount(16).generate()` → pick `vibrantSwatch ?: dominantSwatch ?: mutedSwatch ?: lightVibrantSwatch ?: darkVibrantSwatch ?: lightMutedSwatch ?: darkMutedSwatch` → `Color(swatch.rgb)` else a hardcoded default seed color.
3. Feed that single seed `Color` into `com.materialkolor.dynamicColorScheme(seedColor, isDark, contrastLevel=0.0, style)` where `style` is chosen from `seedColor.toHct().chroma` (Monochrome <4, Neutral <12, else TonalSpot) to get a full `androidx.compose.material3.ColorScheme`. (Optionally support a 4-seed "custom palette" mode by generating 4 sub-schemes and merging role-by-role as ArchiveTune does, if you want a manual multi-swatch theme picker too.)
4. Wrap app content in a `SonaTheme` composable mirroring `ArchiveTuneTheme`: hold `darkTheme`, `pureBlack`, `themeColor`/`seedPalette`, `disableAnimations` params; compute the target `ColorScheme`; optionally apply `pureBlack` override (`surface`/`background` = black); optionally wrap every `ColorScheme` field through `animateColorAsState` for smooth cross-track transitions; feed into `MaterialTheme`/`MaterialExpressiveTheme`.
5. Persist an on/off toggle + manual fallback color (or palette) in DataStore Preferences; fall back to Android 12+ `dynamicDarkColorScheme`/`dynamicLightColorScheme` when dynamic-from-art is enabled but no color has been extracted yet (no track playing) and no manual override is set; fall back to a manual color pre-Android-12 or when the feature is off.
