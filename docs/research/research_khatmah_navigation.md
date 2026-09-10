# Khatmah Navigation & Activity-Hosting Architecture — Research for Sona

Source project: `C:\Users\lhacenmed\AndroidStudioProjects\Khatmah`
CLAUDE.md in that repo is generic coding-guideline boilerplate (no architecture docs) — all
findings below come from reading the actual source.

Package root: `com.lhacenmah.khatmah` → core nav lives in `core/nav/*`, host Activity in `core/*`.

---

## 1. Host activity pattern

### The single reusable host: `ScreenHostActivity`
File: `app/src/main/java/com/lhacenmed/khatmah/core/ScreenHostActivity.kt`

- Extends `BaseComposeActivity` (see below), not `ComponentActivity` directly.
- Declared **once** in the manifest:
  ```xml
  <activity android:name=".core.ScreenHostActivity" android:exported="false" />
  ```
  Every "detail" screen in the app reuses this one Activity — adding a screen never adds a
  manifest entry.
- How it determines which screen to show: the launching `Intent` carries a **single serializable
  extra**, `EXTRA_DEST`, whose value is a `Dest` (sealed class instance — see §2). In `onCreate`:
  ```kotlin
  val dest = (intent.getSerializableExtra(EXTRA_DEST) as? Dest) ?: return finish()
  val content = dest.screen()   // (@Composable () -> Unit)? — Compose body
  val body = dest.fragment()    // Fragment? — native/View body
  if (content == null && body == null) return finish()
  ```
  So the "config" the host needs is entirely encapsulated in one `Dest` object: it supplies
  either a Composable lambda or a Fragment, plus a title/subtitle (for the shared toolbar).
- Layout: inflates `activity_screen_host.xml` — a `LinearLayout` with a `MaterialToolbar`
  (`R.id.toolbar`, fixed height) + a `FrameLayout` body container (`R.id.body`, weight=1).
  - If `dest.title(this) != null`, the toolbar is shown, configured as the support action bar,
    with `setDisplayHomeAsUpEnabled(true)` and the nav (back) button routed through
    `onBackPressedDispatcher.onBackPressed()` (so a screen's own `BackHandler` can intercept it).
  - If title is null, the toolbar is hidden (`View.GONE`) — screen renders full-bleed and draws
    its own top bar/Scaffold (legacy pages).
  - Toolbar/background colors are pulled from the resolved Compose `ColorScheme`
    (`resolveColorScheme(this)`) so native chrome and Compose content share one palette.
- Body rendering:
  - **Fragment body**: `supportFragmentManager.commit { replace(binding.body.id, body) }` (only
    when `savedInstanceState == null`; FragmentManager restores it itself afterward).
  - **Compose body**: creates a `ComposeView`, wraps content in `Theme { Surface { ... } }`,
    provides `CompositionLocalProvider(LocalNavigator provides IntentNavigator(this))`, and calls
    the screen lambda. If chrome (toolbar) is showing, wraps it in
    `Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding())` since insets are handled by
    the host; if no chrome, screen gets full insets responsibility itself.
  - Adds the `ComposeView` to `binding.body` via `FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)`.
- **Reuse mechanism**: this is what "generic/parameterized" means here — the Activity has zero
  per-screen code. The variability lives entirely in the `Dest` sealed subclass supplied via the
  Intent extra. This is effectively a single-Activity architecture for all "pushed" screens,
  while the tab shell (`MainActivity`) and a few legacy screens (Reader, Onboarding) remain
  separate Activities.

### `BaseComposeActivity` (shared base)
File: `core/BaseComposeActivity.kt`
```kotlin
open class BaseComposeActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScale.wrap(newBase))   // custom font/UI scaling wrapper
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTo(this)   // applies day/night + dynamic-color theme overlay
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }
}
```
Only `ScreenHostActivity` extends this currently, but it's designed as the base for any Compose
host Activity — theme application + edge-to-edge + UI-scale wrapping factored out once.

### Other Activities (for contrast — NOT part of the reusable-host pattern)
- `MainActivity` — the app shell: bottom-nav tab host (Fragments in one container, kept alive,
  shown/hidden). Not part of the "add a screen" flow; tabs are added via `AppTabs` list (§3).
- `OnboardingActivity` — a **separate**, self-contained `NavHost` (Navigation-Compose) for the
  one-time onboarding wizard only. This is the *one* place Navigation-Compose is used in the app.
- `ReaderActivity`, `ReaderSearchActivity`, `ReaderSettingsActivity` — legacy/standalone
  Activities for the Quran reader (heavy, custom-rendering screens that predate/don't fit the
  host-model). `Dest.Reader` targets `ReaderActivity::class.java` directly instead of the shared
  host (see `Dest.target`).

So: **one shared host Activity (`ScreenHostActivity`) for the vast majority of screens**, plus a
small number of legacy/special-purpose Activities that opt out via `Dest.target`.

---

## 2. Navigation system

**No Navigation-Compose graph for the main app.** Navigation is a **custom, sealed-class-based,
Intent-driven router**. Navigation-Compose (`NavHost`/`NavController`) is used only inside
`OnboardingActivity`'s tiny internal wizard flow.

### `Dest` — the route catalogue
File: `core/nav/Dest.kt`
```kotlin
sealed class Dest(val target: Class<out Activity>? = null) : java.io.Serializable {
    open fun screen(): (@Composable () -> Unit)? = null      // Compose body (host-model)
    open fun fragment(): Fragment? = null                     // native body (host-model)
    val isHosted: Boolean get() = screen() != null || fragment() != null
    @get:StringRes open val titleRes: Int? get() = null
    open fun title(context: Context): String? = titleRes?.let(context::getString)
    open fun subtitle(context: Context): String? = null
    open fun extras(intent: Intent) {}                        // typed args → Intent extras
    // ... one data object / data class per destination ...
}
```
- Every destination is a `data object` (no args) or `data class` (typed args) nested inside
  `Dest`. Being `Serializable`, an entire `Dest` instance (with its args) rides as a single Intent
  extra — no manual bundling of primitives per screen.
- Two "shapes" of destination:
  1. **Host-model** (the common case): no `target` passed to the `Dest(...)` constructor →
     `isHosted == true`. Supplies `screen()` (a Compose lambda) or `fragment()` (a Fragment
     instance). Rendered by `ScreenHostActivity`. **No Activity class, no manifest entry.**
  2. **Legacy/target-model**: passes `target = SomeActivity::class.java` to the base constructor
     (e.g. `Dest.Reader(...) : Dest(ReaderActivity::class.java)`, or the onboarding destinations
     targeting `OnboardingActivity::class.java`). These override `extras(intent)` to stuff typed
     args as raw Intent extras for that Activity to read (often via a ViewModel's
     `SavedStateHandle`).
- `title(context)`/`titleRes` control whether `ScreenHostActivity` shows its native toolbar
  (non-null title) or the screen renders full-screen with its own chrome (null title, "legacy"
  style, e.g. `Dest.DemoDetail`, `Dest.DbBrowser`).
- Example destinations directly relevant to "add a screen":
  ```kotlin
  data object NewKhatmah : Dest() {
      override val titleRes get() = R.string.new_khatmah_title
      override fun screen() = @Composable { NewKhatmahScreen() }
  }
  data class Sessions(val showRead: Boolean) : Dest() {
      override val titleRes get() = ...
      override fun screen() = @Composable { SessionsScreen(showRead) }
  }
  data object DemoDetail : Dest() {           // no titleRes -> screen draws its own TopAppBar
      override fun screen() = @Composable { DemoDetailScreen() }
  }
  ```

### `IntentNavigator` / `AppNavigator` — the navigation API surface
File: `core/nav/IntentNavigator.kt` (interface `AppNavigator` + `LocalNavigator` declared at the
bottom of `Dest.kt`):
```kotlin
interface AppNavigator {
    fun go(dest: Dest)
    fun back()
}
val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error(...) }

fun Dest.toIntent(context: Context): Intent =
    if (isHosted) {
        Intent(context, ScreenHostActivity::class.java)
            .putExtra(ScreenHostActivity.EXTRA_DEST, this)
            .also { extras(it) }
    } else {
        Intent(context, target!!).also { extras(it) }
    }

class IntentNavigator(private val activity: ComponentActivity) : AppNavigator {
    override fun go(dest: Dest) = activity.startActivity(dest.toIntent(activity))
    override fun back() { activity.finish() }
}
```
- `toIntent()` is the ONE place that decides host-vs-legacy dispatch, and it is generic — adding a
  destination never touches this file.
- Every host Activity (ScreenHostActivity, ComposeTabFragment's tab content, OnboardingActivity)
  provides its own `IntentNavigator(this)` via `CompositionLocalProvider(LocalNavigator provides ...)`.
  Any composable anywhere calls `LocalNavigator.current.go(Dest.Whatever(...))` or `.back()` —
  fully decoupled from knowing which Activity it's running in.
- Back navigation = `activity.finish()`. There is **no in-process back stack management** — the
  platform Activity back stack (and predictive-back / shared-element-less standard transitions)
  is used as-is. Each `go()` starts a **new Activity instance** (even though most routes reuse the
  same `ScreenHostActivity` class); Android's task back stack naturally stacks them.

### Deep linking
- No Android App Links / `<intent-filter>` deep links for in-app navigation.
- "Deep linking" here means internal semantic routing from widgets/notifications:
  `MainActivity.handleLaunchIntent()` reads `intent.action` (e.g.
  `WidgetAction.OPEN_PRAYERS`, or a custom `"com.lhacenmed.khatmah.REMINDER"` action carrying a
  `"route"` string extra) and maps it to a tab index via `AppTabs.indexOfFirst { it.route == route }`,
  then calls `selectTab(index)`. This is unrelated to the `Dest`/`ScreenHostActivity` system —
  it only selects a bottom-nav tab.
- `Dest` destinations don't define deep-link URIs; they're only reachable by constructing a
  `Dest` object in code and calling `nav.go(...)`.

### The one Navigation-Compose usage (OnboardingActivity)
File: `onboarding/OnboardingActivity.kt` — a small **self-contained** `NavHost` for the linear
onboarding wizard (Language → Notifications → Location → Country → City). Routes are plain
strings in `core/nav/ShellRoutes.kt` (with query-param style optional args, e.g.
`"onboarding_city_select?country={country}&iso2={iso2}&fromSettings={fromSettings}"`), registered
with `composable(route, arguments = listOf(navArgument(...) { type = ...; defaultValue = ... }))`.
This pattern is deliberately isolated — comment in the file says "the main app uses Activities +
Dest; this small linear flow keeps a NavHost." Exit points go through a small `OnboardingExit`
class (`toMainApp()` / `toCaller()`) provided via `LocalOnboardingExit`.

---

## 3. "Fewest steps to add a screen" mechanism

No annotations/codegen. The minimalism comes purely from the `Dest` sealed-class + shared-host
design. Concrete recipe, straight from a real code comment + the `DemoDetail` example:

### Recipe: add a new pushed/detail screen (Compose body)
1. **Write the composable** in its feature package, e.g.
   `feature/demo/DemoDetailScreen.kt`:
   ```kotlin
   @Composable
   internal fun DemoDetailScreen() {
       val nav = LocalNavigator.current
       Scaffold(topBar = { TopAppBar(title = { Text(...) }, navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(...) } }) }) { padding ->
           // content
       }
   }
   ```
   (If the `Dest` will supply a `titleRes`/`title()`, the host renders the toolbar and this
   composable does NOT need its own Scaffold/TopAppBar — just body content, e.g.
   `NewKhatmahScreen()`.)
2. **Add one entry to the `Dest` sealed class** (`core/nav/Dest.kt`):
   ```kotlin
   data object DemoDetail : Dest() {
       override fun screen() = @Composable { DemoDetailScreen() }
   }
   ```
   (Add `override val titleRes get() = R.string.xxx` too, if you want the shared native toolbar
   instead of drawing your own.)
3. **Navigate to it** from anywhere with `LocalNavigator.current`:
   ```kotlin
   Button(onClick = { nav.go(Dest.DemoDetail) }) { ... }
   ```
That's it — **2 steps** (composable function + one `Dest` entry); step 3 is just a call site, not
setup. No manifest changes, no new Activity class, no NavGraph registration, no route-string
management (routes are just Kotlin sealed-class members, so they're compile-time type-safe and
refactorable).

### Recipe: add a screen with a native (Fragment/View) body
Same as above, but override `fragment()` instead of `screen()`, returning a `Fragment` instance
(optionally via a `newInstance(...)` factory for args), e.g.:
```kotlin
data object FullIndex : Dest() {
    override val titleRes get() = R.string.full_index_title
    override fun fragment() = FullIndexFragment()
}
```
Fragments add their own toolbar menu items via the standard `MenuProvider` API — the host needs
no per-destination action plumbing.

### Recipe: add a screen with typed arguments
Use a `data class` instead of `data object`; args become constructor params (serialized directly
since `Dest` is `Serializable`):
```kotlin
data class Sessions(val showRead: Boolean) : Dest() {
    override val titleRes get() = if (showRead) R.string.a else R.string.b
    override fun screen() = @Composable { SessionsScreen(showRead) }
}
```
Call site: `nav.go(Dest.Sessions(showRead = true))` — fully type-safe, no string routes, no
Bundle-key juggling for the Compose-body case (args are captured directly in the lambda closure).
For a Fragment-body destination with args, override `extras(intent)` to also put a Bundle extra
the Fragment reads, or use a `newInstance(...)` factory as `AdhkarEditorFragment.newInstance(...)`.

### Recipe: add a new bottom-nav tab (different mechanism — shell-level, not a `Dest`)
Documented directly in `core/nav/AppTab.kt`:
```
Add a tab:
 1. `object YourTab : AppTab(icon, title, route) { … newFragment() }` in its feature package.
 2. Add it to [AppTabs].
```
If the tab body is still Compose, extend `ComposeTab` (subclass of `AppTab`) and implement
`@Composable fun Content(padding: PaddingValues)` instead of `newFragment()` — the abstract class
auto-wraps it in `ComposeTabFragment`. `AppTabs.kt` is a literal `List<AppTab>` — reordering /
adding / removing tabs is a one-line list edit (max 5, `BottomNavigationView` platform limit).

### Recipe: opt a screen out of the shared host (legacy/heavy screens only)
Pass `target = YourActivity::class.java` to the `Dest(...)` base constructor and override
`extras(intent)` to stuff raw Intent extras; requires a manifest `<activity>` entry too. This is
the escape hatch used for `Reader`/`Onboarding`, not the common path.

---

## 4. Activity/Screen boilerplate

### Top-level Compose entry / theming
- `Theme.kt` (`core/ui/theme/Theme.kt`) — the single `@Composable fun Theme(content)` wrapper used
  by **every** Compose entry point in the app (ScreenHostActivity, ComposeTabFragment,
  OnboardingActivity, MainActivity's `updateGate`). It:
  - Reads `resolveColorScheme(context)` — builds a Compose `ColorScheme` by pulling the *native*
    XML theme's resolved Material attributes (`?attr/colorPrimary`, etc.) via one
    `obtainStyledAttributes` pass, so native Views and Compose content always share one palette
    (no separate Compose theme file to keep in sync).
  - Sets `MaterialTheme(colorScheme, typography, content)`.
  - Side-effects the system-bar icon appearance (`WindowInsetsControllerCompat`) to match
    light/dark.
  - No navigation logic lives in `Theme` — it's purely visual/theming, orthogonal to nav.
- Standard usage pattern everywhere a Compose tree is created:
  ```kotlin
  setContent {
      Theme {
          Surface(modifier = Modifier.fillMaxSize()) {
              CompositionLocalProvider(LocalNavigator provides navigator /* + others */) {
                  screenOrTabContent()
              }
          }
      }
  }
  ```
  This 4-layer wrap (Theme → Surface → CompositionLocalProvider(nav) → content) is the
  boilerplate every host repeats; a Sona clone should factor this into one helper, e.g.
  `HostContent(navigator) { ... }`.

### Scaffold convention
No shared/common Scaffold wrapper composable exists — each screen either:
- Has no title (`Dest.titleRes == null`) and builds its own `Scaffold { TopAppBar { ... } }`
  (e.g. `DemoDetailScreen`), or
- Has a title and relies on `ScreenHostActivity`'s native `MaterialToolbar` for chrome, in which
  case the composable is just body content with no Scaffold at all (e.g. `NewKhatmahScreen`,
  `LanguageScreen`, `AboutScreen` etc. — confirmed by `Dest.NewKhatmah` etc. having no visible
  Scaffold usage relative to DemoDetail's explicit one).

### ViewModel pattern
No generic `BaseViewModel` class was found in `core/`. ViewModels are plain
`androidx.lifecycle.ViewModel` subclasses, sometimes with a custom `Factory` (e.g.
`QuranHomeViewModel.Factory(applicationContext)` used in `MainActivity.onCreate` to hoist it to
Activity scope early). Activity-scoped ViewModels shared between the Activity chrome and a tab's
Fragment body use standard `by viewModels()` at the Activity level and `viewModel(activity)` (or
similar) inside the Fragment/Compose body to get the same instance (e.g. `AdhkarViewModel` shared
between `MainActivity`'s contextual toolbar and the Adhkar tab body for selection-mode).
There is no base class abstracting ViewModel boilerplate — this is a "plain ViewModel + explicit
Factory when needed" convention, not a codegen/base-class pattern.

### Fragment bridging for Compose tabs
`ComposeTabFragment` (`core/nav/ComposeTabFragment.kt`) is a small adapter Fragment: looks up the
`ComposeTab` singleton by a `route` string (fragment args, since a Fragment must survive process
death via arguments, not object refs), builds a `ComposeView`, wraps in `Theme { Surface { ... } }`
+ `CompositionLocalProvider(LocalNavigator, LocalTabReselected)`, and calls `tab.Content(padding)`.
It exists only to bridge the fragment-based tab host to Compose tab bodies; comment says it "goes
away with the last [tab]" once everything is native.

### Supporting CompositionLocals
- `LocalNavigator` (`AppNavigator`) — nav actions (`go`/`back`).
- `LocalNavController` (`NavHostController`) — only used inside OnboardingActivity's NavHost.
- `LocalTabReselected` (`Flow<Unit>`) — re-selection signal (tap already-selected tab) for tabs
  implementing `Reselectable`.
- `Reselectable` interface — optional `onReselect()` hook a tab Fragment can implement; wired
  from `MainActivity.reselectTab(index)`.

---

## Recommended design for Sona (com.lhacenmed.sona)

Direct port of the Khatmah pattern, generalized:

1. **One `HostActivity`** (equivalent of `ScreenHostActivity`), declared once in the manifest,
   reading a single `Serializable` sealed-class `Dest`/`Screen` extra from its Intent, rendering
   either a Compose lambda or (optionally, if Sona needs native/Fragment screens too) a Fragment.
   Base it on a small `BaseComposeActivity`-equivalent that applies theme + edge-to-edge.
2. **A `Screen` sealed class** (Sona's `Dest` equivalent) as the single route catalogue — one
   `data object`/`data class` per screen, each supplying:
   - `screen(): @Composable () -> Unit`
   - optional `titleRes`/`title()` to opt into a shared toolbar
   - optional constructor args (auto-serialized since the sealed class is `Serializable`)
3. **`AppNavigator` interface + `IntentNavigator` impl + `LocalNavigator` CompositionLocal** —
   identical shape to Khatmah's. `go(screen)` builds `Intent(context, HostActivity::class.java).putExtra(EXTRA_SCREEN, screen)`;
   `back()` calls `activity.finish()`.
4. **Adding a screen = 2 steps**: (a) write the `@Composable` function, (b) add one `data object`
   entry to the sealed class with `screen = { YourComposable() }`. No manifest edit, no NavGraph
   edit, no DI/codegen step.
5. If Sona wants a bottom-nav/tab shell too, mirror `AppTab`/`AppTabs`/`ComposeTabFragment` as a
   separate, smaller mechanism layered on top — but that's an independent concern from the
   single-host push-screen system and can be added later without touching the `Screen`/`HostActivity`
   design.
6. Reserve Navigation-Compose (`NavHost`) only for a genuinely linear, self-contained sub-flow
   (like onboarding) if Sona ever needs one — keep it out of the main screen system, exactly as
   Khatmah does.

Key files referenced (Khatmah, for side-by-side comparison while building Sona):
- `app/src/main/java/com/lhacenmed/khatmah/core/ScreenHostActivity.kt`
- `app/src/main/java/com/lhacenmed/khatmah/core/BaseComposeActivity.kt`
- `app/src/main/java/com/lhacenmed/khatmah/core/nav/Dest.kt`
- `app/src/main/java/com/lhacenmed/khatmah/core/nav/IntentNavigator.kt`
- `app/src/main/java/com/lhacenmed/khatmah/core/nav/AppTab.kt`
- `app/src/main/java/com/lhacenmed/khatmah/core/nav/AppTabs.kt`
- `app/src/main/java/com/lhacenmed/khatmah/core/nav/ComposeTabFragment.kt`
- `app/src/main/java/com/lhacenmed/khatmah/core/ui/theme/Theme.kt`
- `app/src/main/java/com/lhacenmed/khatmah/feature/demo/DemoDetailScreen.kt` (minimal example screen)
- `app/src/main/java/com/lhacenmed/khatmah/feature/demo/DemoTab.kt` (minimal example tab)
- `app/src/main/java/com/lhacenmed/khatmah/onboarding/OnboardingActivity.kt` (NavHost usage, isolated)
- `app/src/main/res/layout/activity_screen_host.xml`
- `app/src/main/AndroidManifest.xml` (single `ScreenHostActivity` declaration)
