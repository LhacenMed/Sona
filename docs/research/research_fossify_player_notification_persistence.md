# Fossify Music Player — Extraction for Sona port

Source project: `C:\Users\lhacenmed\AndroidStudioProjects\Music` (package `org.fossify.musicplayer`).
NOTE: this fork is heavily modified from upstream Fossify — the bottom-sheet/carousel/player system
is closely modeled on the Auxio music player's UI architecture (vendored `BackportBottomSheetBehavior`,
carousel cover pager, wavy seekbar, synced lyrics). Treat what follows as the actual, current, shipped
implementation to port verbatim.

Relevant Gradle deps (`gradle/libs.versions.toml` / `app/build.gradle.kts`):
- `com.google.android.material:material` version `1.14.0-alpha10` (carries the vendored
  `com.google.android.material.bottomsheet.BackportBottomSheetBehavior` / `BackportBottomSheetDialogFragment`
  used everywhere instead of stock `BottomSheetBehavior`)
- `androidx.media3:media3-session` and `androidx.media3:media3-exoplayer` version `1.10.1`
- `androidx.room:room-runtime` / `room-ktx` / `room-compiler` version `2.8.4`
- `androidx.viewpager2:viewpager2` (used by the cover carousel, `ViewPager2` in `view_playback_panel.xml`)
- Glide (album art loading, no explicit "media3 to build MediaStyle" — see notification section)
- `androidx.compose.material3` (only for non-player screens — the player itself is classic View system)

---

## 1. Full player UI (mini-bar -> expandable bottom sheet -> full player -> stacked queue sheet)

### Architecture summary
There is **no separate "NowPlayingActivity"**. The whole player UI (mini bar + full panel + queue) is
a reusable chunk of UI (`view_playback_sheet.xml`) hosted inside **every** activity that extends
`SimpleMusicActivity` (e.g. `ScreenHostActivity`, `MainActivity`, `TracksActivity`, etc.), via
`setupPlaybackSheet()`. It uses **two nested custom `CoordinatorLayout.Behavior`/`BackportBottomSheetBehavior`
sheets stacked inside one CoordinatorLayout**, not a MotionLayout and not a single BottomSheetBehavior:

1. **Playback sheet** (`R.id.playback_sheet`) — behavior `PlaybackBottomSheetBehavior`. Collapsed = mini
   bar (`current_track_bar`), Expanded = full `PlaybackPanel`.
2. **Queue sheet** (`R.id.queue_sheet`) — behavior `QueueBottomSheetBehavior`, laid out *inside* the
   playback sheet's CoordinatorLayout. Not hideable; anchored above the playback bar via
   `layoutDependsOn`/`onDependentViewChanged` against `current_track_bar`.

Files:
- `app/src/main/kotlin/org/fossify/musicplayer/activities/SimpleMusicActivity.kt` — the state machine
  (full body already quoted above/below); every hosted screen inherits playback UI by calling
  `setupPlaybackSheet()` in `onCreate`.
- `app/src/main/kotlin/org/fossify/musicplayer/activities/ScreenHostActivity.kt` — example host.
- `app/src/main/kotlin/org/fossify/musicplayer/activities/SimpleControllerActivity.kt` — media-controller
  glue (`SimpleMediaController`, `prepareAndPlay`, `playTrack`, `addTracksToQueue`, `removeQueueItems`,
  `playNextInQueue`, `deleteTracks`, `refreshQueueAndTracks`).
- `app/src/main/kotlin/org/fossify/musicplayer/views/PlaybackBottomSheetBehavior.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/QueueBottomSheetBehavior.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/BaseBottomSheetBehavior.kt` (shared base, extends
  vendored `com.google.android.material.bottomsheet.BackportBottomSheetBehavior`)
- `app/src/main/kotlin/org/fossify/musicplayer/views/BottomSheetContentBehavior.kt` (re-insets/re-lays
  out the screen's own body content to make room for however much of the mini-bar is showing)
- `app/src/main/kotlin/org/fossify/musicplayer/views/PlaybackPanel.kt` (the full player: cover carousel,
  seekbar, lyrics, controls, favorite/more/equalizer)
- `app/src/main/kotlin/org/fossify/musicplayer/views/CurrentTrackBar.kt` (the mini-bar)
- `app/src/main/kotlin/org/fossify/musicplayer/views/QueueAdapter.kt` / `QueueDragCallback.kt` /
  `MaterialDragCallback.kt` (drag-to-reorder / swipe-to-remove queue list)
- Layouts: `app/src/main/res/layout/view_playback_sheet.xml`, `view_playback_panel.xml`,
  `view_current_track_bar.xml`, `activity_screen_host.xml`, `item_queue_track.xml`.

### Bottom sheet XML skeleton (`view_playback_sheet.xml`) — verbatim
```xml
<merge xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    tools:parentTag="androidx.coordinatorlayout.widget.CoordinatorLayout">

<View
    android:id="@+id/main_sheet_scrim"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:alpha="0" />

<androidx.coordinatorlayout.widget.CoordinatorLayout
    android:id="@+id/playback_sheet"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:clickable="true"
    android:focusable="true"
    app:layout_behavior="org.fossify.musicplayer.views.PlaybackBottomSheetBehavior">

    <include
        android:id="@+id/current_track_bar"
        layout="@layout/view_current_track_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <!-- The behaviour must sit on a container rather than on the panel itself -->
    <FrameLayout
        android:id="@+id/playback_panel_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="org.fossify.musicplayer.views.BottomSheetContentBehavior">

        <include
            android:id="@+id/playback_panel"
            layout="@layout/view_playback_panel"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </FrameLayout>

    <LinearLayout
        android:id="@+id/queue_sheet"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clickable="true"
        android:elevation="1dp"
        android:focusable="true"
        android:orientation="vertical"
        app:layout_behavior="org.fossify.musicplayer.views.QueueBottomSheetBehavior">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:id="@+id/queue_handle_wrapper"
            android:layout_width="match_parent"
            android:layout_height="@dimen/size_touchable_large">

            <com.google.android.material.bottomsheet.BottomSheetDragHandleView
                android:id="@+id/queue_handle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingBottom="@dimen/spacing_medium"
                app:layout_constraintTop_toTopOf="parent" />

            <TextView
                android:id="@+id/queue_title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/track_queue"
                android:textAppearance="?attr/textAppearanceLabelLargeEmphasized"
                android:textColor="?attr/colorOnSurfaceVariant"
                app:layout_constraintBottom_toBottomOf="@+id/queue_handle"
                app:layout_constraintEnd_toEndOf="@+id/queue_handle"
                app:layout_constraintStart_toStartOf="parent" />
        </androidx.constraintlayout.widget.ConstraintLayout>

        <FrameLayout
            android:id="@+id/queue_content"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <org.fossify.musicplayer.views.BottomInsetRecyclerView
                android:id="@+id/queue_list"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:clipToPadding="false"
                app:layoutManager="org.fossify.commons.views.MyLinearLayoutManager" />

            <com.google.android.material.divider.MaterialDivider
                android:id="@+id/queue_divider"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />
        </FrameLayout>
    </LinearLayout>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
</merge>
```

Host activity layout (`activity_screen_host.xml`) wires it as the last sibling in the root
CoordinatorLayout: `<include layout="@layout/view_playback_sheet" />`.

### Behavior classes (verbatim, load-bearing)
`BaseBottomSheetBehavior` (shared base, `isFitToContents=false`, skip half-expanded, peek height derived
from first child's measured height + gesture insets):
```kotlin
abstract class BaseBottomSheetBehavior<V : View>(context: Context, attributeSet: AttributeSet?) :
    BackportBottomSheetBehavior<V>(context, attributeSet) {
    private var initialized = false
    private val idealBottomGestureInsets = context.resources.getDimensionPixelSize(R.dimen.spacing_medium)

    init { isFitToContents = false }

    abstract fun createBackground(context: Context): Drawable
    abstract fun getIdealBarHeight(context: Context): Int

    open fun applyWindowInsets(child: View, insets: WindowInsets): WindowInsets {
        val gestures = insets.systemGestureInsetsCompat
        val bar = (child as ViewGroup).getChildAt(0)
        peekHeight = if (bar.measuredHeight > 0) {
            bar.measuredHeight + gestures.bottom
        } else {
            getIdealBarHeight(child.context) + gestures.bottom
        }
        return insets
    }

    override fun shouldSkipHalfExpandedStateWhenDragging() = true
    override fun shouldExpandOnUpwardDrag(dragDurationMillis: Long, yPositionPercentage: Float) = true

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        val layout = super.onLayoutChild(parent, child, layoutDirection)
        if (!initialized) {
            child.apply {
                translationZ = context.resources.getDimension(MR.dimen.m3_sys_elevation_level1)
                background = createBackground(context)
                setOnApplyWindowInsetsListener(::applyWindowInsets)
            }
            initialized = true
            peekHeight = getIdealBarHeight(child.context) + idealBottomGestureInsets
        }
        child.requestApplyInsets()
        return layout
    }
}
```
`PlaybackBottomSheetBehavior` (`isHideable = true`; swiping the collapsed bar down hides/stops
playback; `isHideableWhenDragging()` overridden so a drag starting from the *expanded* panel can only
return to collapsed, never hide — vendor-added extension point):
see full source already captured above (constructor, `makeBackgroundDrawable`, `setSurfaceColor`,
`getIdealBarHeight`, `onInterceptTouchEvent`, `isHideableWhenDragging`, `createBackground` (two-layer
`LayerDrawable` so a fading background never reveals a hole), `applyWindowInsets` offsetting
`expandedOffset` by the top system bar inset).

`QueueBottomSheetBehavior` (`isHideable = false`; `expandedOffset` derived from the *measured height of
the current-track bar* (`layoutDependsOn` on `R.id.current_track_bar`) plus spacing, so the queue sheet's
collapsed position always sits exactly above the mini bar) — full source captured above.

`BottomSheetContentBehavior` — generic behavior applied to the *screen's own body content*
(`playback_content` in `activity_screen_host.xml`) so that whatever bottom sheet is currently showing
(found via `layoutDependsOn` on any `BackportBottomSheetBehavior`) eats into the body's bottom system
inset by exactly `calculateConsumedByBar()` (peek height scaled by slide offset while collapsing to
hidden). Full source captured above.

### The state machine driving cross-sheet transitions
`SimpleMusicActivity` implements `ViewTreeObserver.OnPreDrawListener` and recomputes **every visual
transition value on every frame** from the two behaviors' `calculateSlideOffset()` (not from
`BottomSheetCallback.onSlide`, since CoordinatorLayout is "overloaded too much to rely on its usual
listener functionality" per the code comment). Key excerpt (`onPreDraw()`), fully quoted above — computes:
- `sheetScrim.alpha` — dark scrim behind sheets as playback sheet nears full expansion + predictive-back "squish"
- corner-size shrink of `PlaybackBottomSheetBehavior.sheetBackgroundDrawable` during predictive back
- `trackBar.alpha` vs `panel.alpha` vs `queueContent.alpha` cross-fade as playback sheet slides 0→1 and
  queue sheet slides 0→1 (two independently driven ratios combined with `min`/`max`)
- `playbackSheetBehavior.isDraggable = queueSheetBehavior.state == STATE_COLLAPSED` (queue expanded ⇒
  outer sheet can't be dragged, so its drag gesture doesn't fight the queue's)
- Predictive-back (Android 13+ `OnBackPressedCallback`/`BackEventCompat`) is wired through
  `SheetBackPressedCallback`, delegating `startBackProgress`/`updateBackProgress`/`handleBackInvoked`/
  `cancelBackProgress` to whichever sheet (queue takes priority over playback) is currently shown — this
  is the vendored `BackportBottomSheetBehavior`'s own predictive-back API, not hand-rolled.

Sheet open/close verbs:
```kotlin
fun tryOpenPlaybackPanel() {
    if (playbackSheetBehavior.targetState == BackportBottomSheetBehavior.STATE_COLLAPSED) {
        playbackSheetBehavior.state = BackportBottomSheetBehavior.STATE_EXPANDED
        return
    }
    if (playbackSheetBehavior.state == BackportBottomSheetBehavior.STATE_EXPANDED &&
        queueSheetBehavior.targetState == BackportBottomSheetBehavior.STATE_EXPANDED) {
        queueSheetBehavior.state = BackportBottomSheetBehavior.STATE_COLLAPSED
    }
}
private fun tryClosePlaybackPanel() {
    if (playbackSheetBehavior.targetState == BackportBottomSheetBehavior.STATE_EXPANDED) {
        playbackSheetBehavior.state = BackportBottomSheetBehavior.STATE_COLLAPSED
        queueSheetBehavior.state = BackportBottomSheetBehavior.STATE_COLLAPSED
    }
}
private fun tryShowSheets() { /* STATE_HIDDEN -> STATE_COLLAPSED, isDraggable = true */ }
private fun tryHideAllSheets() { /* isDraggable = false, state = STATE_HIDDEN */ }
```
Mini bar tap expands the panel (`trackBar.setOnClickListener { tryOpenPlaybackPanel() }`); swiping the
mini bar down (`STATE_HIDDEN` reached while a track is still loaded) calls `stopPlayback()`, which sends
`CustomCommands.CLOSE_PLAYER` to the service (stops playback for good — see persistence section).
`PlaybackPanel`'s toolbar back arrow (`ic_down_24`) calls `tryClosePlaybackPanel()`.

### Cover carousel (horizontal artwork swipe)
`ViewPager2` (`R.id.playback_pager`, aspect-ratio 1:1) + `CoverPagerAdapter` (Glide-loaded cover per
page, `offscreenPageLimit = 1`) + `CarouselTransformer` (custom `PageTransformer` implementing Material's
carousel "masking" look via `MaskableFrameLayout.setMaskRectF`, piecewise gap/reveal function, and 0.2x
parallax on inner content — full source captured above) + `UserAwarePagerCallback` (distinguishes a
user-drag page-settle from a programmatic `setCurrentItem`, since only a genuine user swipe should issue
a `player.seekTo(target, 0)`).

`PagerCommand` scheduling (`PlaybackPanel.schedulePagerCommand`/`applyPagerCommand`) defers moving the
pager until the *next committed frame* (`registerFrameCommitCallback` on API 29+, double
`postOnAnimation` fallback below), and distinguishes an adjacent skip-next/prev (`smoothScrollByPageTo`,
animated) from any other jump (`setCurrentItem(pos, false)`, instant) — this is what makes swipes for
skip-next/prev animate while a queue replacement or reshuffle snaps instantly. Full function bodies
captured above in `PlaybackPanel.kt` (`updateCarousel`, `schedulePagerCommand`, `applyPagerCommand`,
`playerIndexOf`, `pagerPositionOf`).

### Seek bar
Custom `StyledSeekBar` (`app/src/main/kotlin/org/fossify/musicplayer/views/StyledSeekBar.kt`) wraps a
Material `Slider` (not a classic `SeekBar`) with wavy active-track rendering
(`slider.setWaveEnabled(...)`, a Material3-expressive API). Values are stored/exchanged in
**deciseconds** (1/10 s) throughout, `positionDs`/`durationDs` properties with clamping guards against
NaN/negative/out-of-range. `Listener.onSeekConfirmed(positionDs)` fires only in `onStopTrackingTouch`
(i.e., commit-on-release, not drag-live) → `withPlayer { seekTo(positionDs * 100) }`.
Live position ticking: `PlaybackPanel.scheduleProgressUpdate()` polls `player.currentPosition` every
100ms via `Handler.postDelayed`, adjusted by `context.config.playbackSpeed` — *not* driven by media3
listener callbacks (those fire on player events only, not continuously). Progress push:
`binding.playbackSeekBar?.positionDs = currentPosition / 100`. The mini bar's own progress
(`current_track_progress`, a `LinearProgressIndicator`) is ticked identically but independently from
`SimpleMusicActivity.scheduleTrackBarProgress`.

### Playback controls, favorite, more, equalizer
All wired directly in `PlaybackPanel.initialize(...)` (full source above): `playbackPlayPause` →
`togglePlayback()`; `playbackSkipPrev`/`playbackSkipNext` → `seekToPrevious()`/`seekToNext()` which
**coalesce rapid taps** over a 150ms window (`seekWithDelay`/`seekByCount`) before issuing a single
`player.seekTo(index, 0)` (worked around because media3 seek-to-adjacent-item is otherwise slow —
references `androidx/media/issues/81`); `playbackShuffle` toggles `context.config.isShuffleEnabled` then
asks the player (`shuffleModeEnabled = it`), letting the service deal a fresh shuffle order rather than
computing one locally; `playbackRepeat` cycles a 4-state `PlaybackSetting` enum
(`REPEAT_OFF → REPEAT_PLAYLIST → REPEAT_TRACK → STOP_AFTER_CURRENT_TRACK → …`) persisted to
`context.config.playbackSetting`; `playbackFavorite` toggles membership in a built-in "Favorites"
playlist via `AudioHelper.setFavorite` (optimistic UI: icon flips instantly, DB write happens on a
background thread, guarded against races with a `favoriteToken` counter); `playbackMore` opens
`TrackMenuDialog.showForPlayingTrack`; `playbackToolbar`'s menu (`menu_playback_panel`) exposes
"Open Equalizer" which launches `EqualizerActivity`.

`TrackMenuDialog` (`app/src/main/kotlin/org/fossify/musicplayer/dialogs/TrackMenuDialog.kt`) is a
`BackportBottomSheetDialogFragment` (vendor bottom-sheet dialog, not the stock one) that inflates a menu
resource (`R.menu.menu_playback_track` for the "more" button on the now-playing screen, or
`R.menu.menu_track` for a track row elsewhere) into a plain `RecyclerView` of rows (`MenuOptionAdapter`),
each row's icon/text pulled straight from the `MenuItem`. Actions dispatch back onto
`SimpleControllerActivity`: Play, Shuffle, Add to Playlist, Play Next, Add to Queue, Go to Artist/Album,
Properties, Share. Full source captured above.

### Lyrics
`LyricsView` (custom `FrameLayout` wrapping a `RecyclerView` + `LyricsAdapter`) shows lyrics *inline in
the full player panel* (`playbackLyrics` in the ViewBinding, referenced in `PlaybackPanel`), not as a
separate screen. Supports both plain (`Lyrics.Plain`, just scrollable) and synced
(`Lyrics.Synced`/`TimedLine` list, auto-highlighted + auto-centered as `positionMs` advances) via
`LyricsState` (`Loading` / `Loaded(lyrics)` / `Empty`). Active line lookup uses a hint-based fast path
plus a binary-search fallback (`indexAt`/`search`), and centers the active line with a custom
`LinearSmoothScroller` (`CenterSmoothScroller`, 90 ms/inch — deliberately slow drift, not a snap).
Lyrics are only shown "where the screen is tall enough" per code comment — i.e., conditionally inflated/
visible depending on available vertical space; the binding property `playbackLyrics` is nullable.
Fetched via `LyricsExtractor.extract(track)` off the main thread in `PlaybackPanel.loadLyrics`, with a
`lyricsToken` counter to drop stale results after the user has moved to another track.

---

## 2. Media notification

**This app does not build a custom MediaStyle notification by hand.** It runs
`androidx.media3.session.MediaLibraryService` (`PlaybackService`, which extends
`MediaLibraryService` and implements `MediaSessionService.Listener`), and lets **Media3's own automatic
media notification** (the library's `DefaultMediaNotificationProvider`, wired in internally by
`MediaSessionService`) build/update/post the ongoing playback notification, media-session-driven, from
the `ExoPlayer` + `MediaSession` state. There is no `setMediaNotificationProvider(...)` override anywhere
in the codebase (`grep` for `MediaNotification`/`NotificationProvider` finds nothing) — the app relies on
the default provider entirely.

`app/src/main/kotlin/org/fossify/musicplayer/playback/NotificationHelper.kt` exists but is **not** the
media notification — it only builds three unrelated, plain `NotificationCompat` notifications:
1. `createNoPermissionNotification()` — shown via `startForeground` when storage permission is missing
   (used from `PlaybackService.showNoPermissionNotification()`, delayed 100ms).
2. `createMediaScannerNotification(...)` — library-scan progress.
3. `createUpdateNotification(...)` — app self-update download progress.
All three share channel `music_player_channel` (`NotificationManager.IMPORTANCE_LOW`, no lights/vibration/
badge), created lazily via `NotificationHelper.createInstance(context)`.

### Session/player setup that feeds the automatic notification
`app/src/main/kotlin/org/fossify/musicplayer/playback/PlayerInit.kt`:
```kotlin
internal fun PlaybackService.initializeSessionAndPlayer(
    handleAudioFocus: Boolean,
    handleAudioBecomingNoisy: Boolean
) {
    player = initializePlayer(handleAudioFocus, handleAudioBecomingNoisy)
    playerListener = getPlayerListener()
    mediaSession =
        MediaLibraryService.MediaLibrarySession.Builder(this, player, getMediaSessionCallback())
            .setSessionActivity(getSessionActivityIntent())
            .build()

    withPlayer {
        addListener(playerListener)
        setRepeatMode(config.playbackSetting)
        setPlaybackSpeed(config.playbackSpeed)
        shuffleModeEnabled = config.isShuffleEnabled
        mediaSession.setCustomLayout(getCustomLayout())
        SimpleEqualizer.setupEqualizer(this@initializeSessionAndPlayer, player)
    }
}
```
`getSessionActivityIntent()` builds the notification's tap-target `PendingIntent` (opens `MainActivity`
with `EXTRA_OPEN_PLAYER=true`, `FLAG_ACTIVITY_NEW_TASK|CLEAR_TOP|SINGLE_TOP`).

`ExoPlayer` is built with `C.WAKE_MODE_LOCAL`, `AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC)`
and audio-focus handling `true`, `setHandleAudioBecomingNoisy(true)`, custom seek increments
(`SEEK_INTERVAL_MS`), and a custom `AudioOnlyRenderersFactory`.

### Notification action buttons — via `CommandButton` custom layout, not manual PendingIntents
`app/src/main/kotlin/org/fossify/musicplayer/playback/CustomCommands.kt`:
```kotlin
enum class CustomCommands(val customAction: String) {
    CLOSE_PLAYER(customAction = PATH + "CLOSE_PLAYER"),
    RELOAD_CONTENT(customAction = PATH + "RELOAD_CONTENT"),
    TOGGLE_SLEEP_TIMER(customAction = PATH + "TOGGLE_SLEEP_TIMER"),
    SET_NEXT_ITEM(customAction = PATH + "SET_NEXT_ITEM"),
    SET_SHUFFLE_ORDER(customAction = PATH + "SET_SHUFFLE_ORDER");
    val sessionCommand = SessionCommand(customAction, Bundle.EMPTY)
    companion object {
        fun fromSessionCommand(sessionCommand: SessionCommand): CustomCommands? =
            values().find { it.customAction == sessionCommand.customAction }
    }
}
internal val customCommands = CustomCommands.values().map { it.sessionCommand }
internal fun Context.getCustomLayout(): List<CommandButton> = listOf(
    CommandButton.Builder()
        .setDisplayName(getString(org.fossify.commons.R.string.close))
        .setSessionCommand(CustomCommands.CLOSE_PLAYER.sessionCommand)
        .setIconResId(org.fossify.commons.R.drawable.ic_cross_vector)
        .build()
)
```
So beyond media3's built-in play/pause/skip-next/skip-prev (auto-derived from `Player.Commands`), the
**only extra notification button is "Close"**, added via `MediaSession.setCustomLayout(...)` /
`mediaSession.setCustomLayout(controller, customLayout)` (re-sent in `onPostConnect` for newer
controllers). There is no favorite button on the notification in this codebase.

`MediaSessionCallback.onCustomCommand` dispatches these:
```kotlin
when (command) {
    CustomCommands.CLOSE_PLAYER -> stopService()
    CustomCommands.RELOAD_CONTENT -> reloadContent()
    CustomCommands.TOGGLE_SLEEP_TIMER -> toggleSleepTimer()
    CustomCommands.SET_SHUFFLE_ORDER -> setShuffleOrder(args)
    CustomCommands.SET_NEXT_ITEM -> setNextItem(args)
}
```
(`onConnect` adds all `customCommands` to `availableSessionCommands` for every controller.)

### Album art / lock-screen metadata
Metadata (title/artist/artwork/duration) is **not manually pushed via `MediaSessionCompat.setMetadata`**
— media3's session derives it automatically from each `MediaItem`'s `MediaMetadata` (built when tracks
are turned into `MediaItem`s, see `models/toMediaItems`/`toMediaItem` — not read in this pass, but that's
where `MediaMetadata.Builder().setArtworkUri(...)`/`setTitle`/`setArtist`/etc. would live for a media3
app). Media3's `DefaultMediaNotificationProvider` resolves `MediaMetadata.artworkUri` /
`MediaMetadata.artworkData` through its own bitmap loader/cache for the notification, so there is no
custom Glide-into-notification code path (unlike the mini-bar/cover-carousel/lyrics UI, which do use
Glide, e.g. `CurrentTrackBar.updateCurrentTrack`, `CoverViewHolder.loadCover`).

### Service lifecycle
```kotlin
class PlaybackService : MediaLibraryService(), MediaSessionService.Listener {
    override fun onCreate() {
        super.onCreate()
        setListener(this)
        playHistoryRecorder = PlayHistoryRecorder(this)
        initializeSessionAndPlayer(handleAudioFocus = true, handleAudioBecomingNoisy = true)
        initializeLibrary()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
    override fun onDestroy() {
        super.onDestroy()
        releaseMediaSession(); clearListener(); playHistoryRecorder.release()
        stopSleepTimer(); SimpleEqualizer.release()
    }
    fun stopService() {
        withPlayer { pause(); clearMediaItems() }
        mediaItemProvider.clearRecentItems()
        stopSelf()
    }
    override fun onForegroundServiceStartNotAllowedException() {
        showErrorToast(getString(org.fossify.commons.R.string.unknown_error_occurred))
        // todo: show a notification instead.
    }
    companion object {
        var isPlaying: Boolean = false; private set
        var currentMediaItem: MediaItem? = null; private set
        var nextMediaItem: MediaItem? = null; private set
        fun updatePlaybackInfo(player: Player) {
            currentMediaItem = player.currentMediaItem
            nextMediaItem = player.nextMediaItem
            isPlaying = player.isReallyPlaying
        }
    }
}
```
Note the `companion object` cache of `currentMediaItem`/`isPlaying`/`nextMediaItem`: every UI class reads
`PlaybackService.currentMediaItem` synchronously (e.g. to decide sheet visibility) rather than paying for
a `MediaController` round trip, explicitly to keep UI responsive.

---

## 3. Playback-state persistence (queue/position restore)

### Storage: Room table `queue_items`
`app/src/main/kotlin/org/fossify/musicplayer/data/dao/QueueItemsDao.kt`:
```kotlin
@Dao
interface QueueItemsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(queueItems: List<QueueItem>)

    @Query("SELECT * FROM queue_items ORDER BY track_order")
    fun getAll(): List<QueueItem>

    @Query("UPDATE queue_items SET is_current = 0")
    fun resetCurrent()

    @Query("SELECT * FROM queue_items WHERE is_current = 1")
    fun getCurrent(): QueueItem?

    @Query("UPDATE queue_items SET is_current = 1 WHERE track_id = :trackId")
    fun saveCurrentTrack(trackId: Long)

    @Query("UPDATE queue_items SET is_current = 1, last_position = :lastPosition WHERE track_id = :trackId")
    fun saveCurrentTrackProgress(trackId: Long, lastPosition: Int)

    @Query("UPDATE queue_items SET track_order = :order WHERE track_id = :trackId")
    fun setOrder(trackId: Long, order: Int)

    @Query("DELETE FROM queue_items WHERE track_id = :trackId")
    fun removeQueueItem(trackId: Long)

    @Query("DELETE FROM queue_items")
    fun deleteAllItems()
}
```
Model (`models/QueueItem.kt`):
```kotlin
data class QueueItem(
    @ColumnInfo(name = "track_id") var trackId: Long,
    @ColumnInfo(name = "track_order") var trackOrder: Int,
    @ColumnInfo(name = "is_current") var isCurrent: Boolean,
    @ColumnInfo(name = "last_position") var lastPosition: Int
) {
    companion object {
        fun from(id: Long, position: Int = 0): QueueItem = QueueItem(id, 0, true, position)
    }
}
```
`last_position` is stored in **whole seconds** (int), not ms — see the ms↔s conversions below.

### Write path — `AudioHelper` (`app/src/main/kotlin/org/fossify/musicplayer/data/AudioHelper.kt`)
```kotlin
fun clearQueue() {
    context.queueDAO.deleteAllItems()
}

fun resetQueue(items: List<QueueItem>, currentTrackId: Long? = null, startPosition: Long? = null) {
    context.queueDAO.deleteAllItems()
    context.queueDAO.insertAll(items)
    if (currentTrackId != null && startPosition != null) {
        val startPositionSeconds = startPosition.milliseconds.inWholeSeconds.toInt()
        context.queueDAO.saveCurrentTrackProgress(currentTrackId, startPositionSeconds)
    } else if (currentTrackId != null) {
        context.queueDAO.saveCurrentTrack(currentTrackId)
    }
}
```
Every save is a **full delete-and-reinsert** of the whole queue table (not incremental), keeping it
trivially consistent with whatever `mediaItems` order the player currently holds.

### When persistence happens: on (almost) every player event
`app/src/main/kotlin/org/fossify/musicplayer/playback/PlayerListener.kt`:
```kotlin
override fun onEvents(player: Player, events: Player.Events) {
    if (events.containsAny(
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_TRACKS_CHANGED,
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAYLIST_METADATA_CHANGED
        )
    ) {
        updatePlaybackState()
    }
    if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
        playHistoryRecorder.onPlayingChanged(player.isReallyPlaying, player.currentPosition)
    }
}
```
`updatePlaybackState()` (`PlayerInit.kt`):
```kotlin
internal fun PlaybackService.updatePlaybackState() {
    withPlayer {
        updatePlaybackInfo(player)
        broadcastUpdateWidgetState()
        val currentMediaItem = currentMediaItem
        if (currentMediaItem != null) {
            mediaItemProvider.saveRecentItemsWithStartPosition(
                mediaItems = currentMediaItems,
                current = currentMediaItem,
                startPosition = currentPosition
            )
        }
    }
}
```
So the queue + current track + position is re-saved **on essentially every relevant Player event** —
transitions, seeks/discontinuities, timeline changes (queue edits), play/pause toggles — not on a timer
and not only at pause/destroy. `MediaItemProvider.saveRecentItemsWithStartPosition` (already shown above)
runs the actual DB write on a **single-thread `Executor`** (`MoreExecutors.listeningDecorator`), so writes
are serialized and off the player thread:
```kotlin
fun saveRecentItemsWithStartPosition(mediaItems: List<MediaItem>, current: MediaItem, startPosition: Long) {
    if (mediaItems.isEmpty()) return
    executor.execute {
        val trackId = current.mediaId.toLong()
        val queueItems = mediaItems.mapIndexed { index, mediaItem ->
            QueueItem(trackId = mediaItem.mediaId.toLong(), trackOrder = index, isCurrent = false, lastPosition = 0)
        }
        audioHelper.resetQueue(queueItems, trackId, startPosition)
    }
}
fun clearRecentItems() {
    executor.execute { audioHelper.clearQueue() }
}
```
Stopping playback for good (mini-bar swipe-to-dismiss, or notification's Close button →
`CustomCommands.CLOSE_PLAYER` → `PlaybackService.stopService()`) explicitly empties the player
(`clearMediaItems()`) **and** clears the persisted queue (`mediaItemProvider.clearRecentItems()`) on the
*same executor* so a save already in flight can't race it and resurrect the queue — this is what keeps
the player closed across future launches until the user starts something new.

### Restore path — triggered by `maybePreparePlayer`, before/independent of library scan
`app/src/main/kotlin/org/fossify/musicplayer/extensions/Player.kt`:
```kotlin
var prepareInProgress = false
inline fun Player.maybePreparePlayer(context: Context, crossinline callback: (success: Boolean) -> Unit) {
    if (!prepareInProgress && currentMediaItem == null) {
        prepareInProgress = true
        ensureBackgroundThread {
            var prepared = false
            context.audioHelper.getQueuedTracksLazily { tracks, startIndex, startPositionMs ->
                if (!prepared) {
                    prepareUsingTracks(tracks = tracks, startIndex = startIndex, startPositionMs = startPositionMs) {
                        callback(it)
                        prepared = it
                    }
                } else {
                    if (tracks.size == 1) return@getQueuedTracksLazily
                    addRemainingMediaItems(tracks.toMediaItemsFast(), startIndex)
                }
            }
        }
    } else {
        callback(false)
    }
}
```
Called from `SimpleControllerActivity.onCreate`/`onResume` (`maybePreparePlayer()`), i.e. **triggered by
the first UI that attaches a `MediaController`**, guarded by a global `prepareInProgress` flag so only one
restore ever runs even if multiple activities attach concurrently, and by `currentMediaItem == null` so
it's a no-op once something is already loaded. It is independent of the library media-tree scan
(`MediaItemProvider.reload()`/`buildTracks()` etc.) — the restore reads track rows straight from
`tracksDAO`/`getTrack(id)` inside `AudioHelper`, not from the `MediaItemProvider` tree, and reports the
**current track first** (`callback(listOf(currentTrack), 0, startPositionMs)`) before the rest of the
queue is resolved, specifically to start audio as fast as possible; the full queue then arrives in the
same callback a moment later and is spliced in via `addRemainingMediaItems`.

`AudioHelper.getQueuedTracksLazily` (`data/AudioHelper.kt`):
```kotlin
fun getQueuedTracksLazily(callback: (tracks: List<Track>, startIndex: Int, startPositionMs: Long) -> Unit) {
    ensureBackgroundThread {
        val queueItems = context.queueDAO.getAll()
        val currentItem = context.queueDAO.getCurrent()
        if (currentItem == null) {
            callback(emptyList(), 0, 0)
            return@ensureBackgroundThread
        }
        val currentTrack = getTrack(currentItem.trackId)
        if (currentTrack == null) {
            callback(emptyList(), 0, 0)
            return@ensureBackgroundThread
        }
        val startPositionMs = currentItem.lastPosition.seconds.inWholeMilliseconds
        callback(listOf(currentTrack), 0, startPositionMs)   // fast path: current track only

        val queuedTracks = getQueuedTracks(queueItems)        // full queue, same order as track_order
        val currentIndex = queuedTracks.indexOfFirstOrNull { it.mediaStoreId == currentTrack.mediaStoreId } ?: 0
        callback(queuedTracks, currentIndex, startPositionMs)
    }
}
```
`getQueuedTracks(queueItems)` cross-references `queue_items.track_id` against
`getAllTracks().associateBy { it.mediaStoreId }`, preserving `track_order`, and flags whichever row
`isCurrent` with `FLAG_IS_CURRENT` on the returned `Track`.

Also used at the media-session level for out-of-app resumption (Android 12+/Auto):
`MediaSessionCallback.onPlaybackResumption` calls `mediaItemProvider.getRecentItemsLazily { ... }`, which
in turn calls the **exact same** `audioHelper.getQueuedTracksLazily` when the media tree isn't ready yet,
or (once the tree is built) `getRecentItemsWithStartPosition()` which re-derives everything from
`context.queueDAO.getAll()`/`getCurrent()` mapped through the already-built `MediaItemProvider` tree nodes
(`getMediaItemFromQueueItem`) instead of re-hitting the tracks table.

### Shuffle / repeat persistence
Not stored in Room — stored in the app's `Config`/SharedPreferences (`context.config`), separately from
the queue table:
- `PlayerListener.onShuffleModeEnabledChanged` → `config.isShuffleEnabled = shuffleModeEnabled`
- `PlayerListener.onRepeatModeChanged` → `config.playbackSetting = getPlaybackSetting(repeatMode)`
  (skipped when `playbackSetting == STOP_AFTER_CURRENT_TRACK`, a 4th custom mode media3's own
  `REPEAT_MODE_*` can't represent, handled manually by seeking to 0 + pausing on
  `MEDIA_ITEM_TRANSITION_REASON_REPEAT`).
Restored in `PlayerInit.initializeSessionAndPlayer`:
```kotlin
withPlayer {
    addListener(playerListener)
    setRepeatMode(config.playbackSetting)
    setPlaybackSpeed(config.playbackSpeed)
    shuffleModeEnabled = config.isShuffleEnabled
    ...
}
```
i.e. applied to the freshly built `ExoPlayer` immediately on service creation, before any queue restore
happens — shuffle re-ordering of the *actual restored queue* then happens naturally once
`shuffleModeEnabled=true` is applied and the media items are set (media3 deals a shuffle order over
whatever timeline it's given).

### Custom shuffle-order persistence-adjacent detail
Manual queue reordering while shuffled doesn't call `Player.moveMediaItem` (which would operate on the
underlying, unshuffled item order); instead `SimpleMusicActivity.movePlayOrderEntry` sends a custom
session command `CustomCommands.SET_SHUFFLE_ORDER` with an `EXTRA_SHUFFLE_INDICES` int array, handled in
`MediaSessionCallback.setShuffleOrder`:
```kotlin
private fun setShuffleOrder(args: Bundle) {
    val indices = args.getIntArray(EXTRA_SHUFFLE_INDICES) ?: return
    withPlayer { setShuffleIndices(indices) }
}
```
(`setShuffleIndices` lives on `SimpleMusicPlayer`, not opened in this pass — flag for the implementer if
shuffle-drag-to-reorder needs porting.)

---

## Files map (all paths absolute, for the porting engineer)

Player UI:
- `app/src/main/kotlin/org/fossify/musicplayer/activities/SimpleMusicActivity.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/activities/SimpleControllerActivity.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/activities/ScreenHostActivity.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/PlaybackPanel.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/CurrentTrackBar.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/PlaybackBottomSheetBehavior.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/QueueBottomSheetBehavior.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/BaseBottomSheetBehavior.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/BottomSheetContentBehavior.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/StyledSeekBar.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/LyricsView.kt`, `LyricsAdapter.kt`, `LyricsState.kt`, `LyricsDensity.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/CoverPagerAdapter.kt`, `CarouselTransformer.kt`, `UserAwarePagerCallback.kt`, `PagerCommand.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/views/QueueAdapter.kt`, `QueueDragCallback.kt`, `MaterialDragCallback.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/dialogs/TrackMenuDialog.kt`
- `app/src/main/res/layout/view_playback_sheet.xml`, `view_playback_panel.xml`, `view_current_track_bar.xml`, `activity_screen_host.xml`, `item_queue_track.xml`

Notification/session:
- `app/src/main/kotlin/org/fossify/musicplayer/playback/PlaybackService.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/playback/PlayerInit.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/playback/MediaSessionCallback.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/playback/CustomCommands.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/playback/PlayerListener.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/playback/NotificationHelper.kt` (non-media notifications only)
- `app/src/main/kotlin/org/fossify/musicplayer/playback/MediaItemProvider.kt`

Persistence:
- `app/src/main/kotlin/org/fossify/musicplayer/data/dao/QueueItemsDao.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/data/AudioHelper.kt` (`isFavorite`, `setFavorite`,
  `getQueuedTracks`, `getQueuedTracksLazily`, `clearQueue`, `resetQueue`)
- `app/src/main/kotlin/org/fossify/musicplayer/models/QueueItem.kt`
- `app/src/main/kotlin/org/fossify/musicplayer/extensions/Player.kt` (`maybePreparePlayer`, `prepareUsingTracks`, `addRemainingMediaItems`)
- `app/src/main/kotlin/org/fossify/musicplayer/playback/PlayHistoryRecorder.kt` (play-count/history side effect, not queue persistence, but fires off the same listener)
