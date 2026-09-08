# Auxio Secondary Settings — Implementation Research (for Sona clone)

Source repo: `C:\Users\lhacenmed\AndroidStudioProjects\Auxio`
All package roots referenced below: `org.oxycblt.auxio` (app module) and `org.oxycblt.musikr` (a
separate library module at `musikr/`, containing the tagging/indexing/model layer — this is a
distinct Gradle module, not a subpackage of the app).

## 0. Settings infrastructure (shared plumbing)

- No DataStore/Proto DataStore is used anywhere. Everything is plain `SharedPreferences` via
  `androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)`.
- File: `app/src/main/java/org/oxycblt/auxio/settings/Settings.kt`
  - `interface Settings<Listener>` with `migrate()`, `registerListener()`, `unregisterListener()`.
  - `abstract class Impl<Listener>(context) : Settings<Listener>, SharedPreferences.OnSharedPreferenceChangeListener`
    holds `protected val sharedPreferences: SharedPreferences`, exposes `getString(@StringRes id)`
    helper, and dispatches `onSettingChanged(key, listener)` (single listener per Impl instance,
    not a list) whenever any pref key changes.
- Each settings area gets its own small interface + `*Impl` class implementing `Settings.Impl<X.Listener>`,
  injected via Hilt (`@Inject constructor(@ApplicationContext context: Context)`), e.g.
  `PlaybackSettings`/`PlaybackSettingsImpl`, `MusicSettings`/`MusicSettingsImpl`,
  `ImageSettings`/`ImageSettingsImpl`, `HomeSettings`/`HomeSettingsImpl`.
- Preference **keys are string resources**, defined in `app/src/main/res/values/settings.xml` (all
  `translatable="false"`), and read back at runtime with `getString(R.string.set_key_xxx)` — i.e.
  the key literal never appears directly in Kotlin code, only the resource id. Enum-like settings
  are stored as `Int` (an opaque "int code" defined in `IntegerTable.kt`, arbitrary hex constants
  like `0xA110`) rather than storing the enum name string.
- Preference **UI** is classic `androidx.preference` XML screens (`PreferenceFragmentCompat`
  subclasses), not Compose: `app/src/main/res/xml/preferences_audio.xml`,
  `preferences_music.xml`, `preferences_personalize.xml`, etc. Each category has a matching
  Kotlin `*PreferenceFragment` under `app/src/main/java/org/oxycblt/auxio/settings/categories/`
  extending `BasePreferenceFragment(R.xml.preferences_xxx)`, which can override
  `onOpenDialogPreference` (to navigate to a custom dialog for `WrappedDialogPreference`s) and
  `onSetupPreference` (to attach `onPreferenceChangeListener`s that trigger side effects like
  cache clears / library reloads).
- For Sona (Kotlin/Compose or XML — whichever the app uses), the direct equivalent is:
  SharedPreferences + a small `Settings<Listener>` interface pattern, or if Sona already uses
  Jetpack DataStore, port each `val/var` accessor 1:1, translating the SharedPreferences get/put
  calls into DataStore `Preferences.Key` reads (same key strings can be reused for a smooth port).

---

## 1. Audio playback settings

File: `app/src/main/java/org/oxycblt/auxio/playback/PlaybackSettings.kt`

Interface (`PlaybackSettings`) exposes, among others:
```kotlin
val headsetAutoplay: Boolean   // key: set_key_headset_autoplay = "auxio_headset_autoplay"
val keepShuffle: Boolean       // key: set_key_keep_shuffle     = "KEY_KEEP_SHUFFLE"      (default true)
val rewindWithPrev: Boolean    // key: set_key_rewind_prev      = "KEY_PREV_REWIND"       (default true)
val pauseOnRepeat: Boolean     // key: set_key_repeat_pause     = "KEY_LOOP_PAUSE"        (default false)
val rememberPause: Boolean     // key: set_key_remember_pause   = "auxio_remember_pause"  (default false)
val exitOnTaskRemoval: Boolean // key: set_key_task_exit
```
All are simple `sharedPreferences.getBoolean(getString(R.string.set_key_x), default)` getters.
Preference XML: `app/src/main/res/xml/preferences_audio.xml` (category "Playback") defines the
four `SwitchPreferenceCompat`s for headset autoplay, rewind-with-prev (default true), pause-on-
repeat (default false), remember-pause (default false).

Listener interface fires two granular callbacks that consumers care about:
```kotlin
interface Listener {
    fun onReplayGainSettingsChanged() {}
    fun onBarActionChanged() {}
    fun onPauseOnRepeatChanged() {}
}
```
(`keepShuffle`/`rewindWithPrev`/`rememberPause`/`headsetAutoplay` are read live, on-demand, at the
moment of the playback action — there's no dedicated changed-callback for them.)

### Consumption points — `ExoPlaybackStateHolder`
File: `app/src/main/java/org/oxycblt/auxio/playback/service/ExoPlaybackStateHolder.kt`
(this is the `Player.Listener` + `PlaybackStateHolder` wrapping a real `ExoPlayer` instance).

**Remember pause** (`rememberPause`): Auxio's *default* skip/seek behavior auto-resumes playback
whenever the current song changes via a user action. `rememberPause=false` (default) reproduces
that: after `next()`, `prev()`, `goto(index)`, or `remove()` (when the removed item was the one
currently playing), the code calls `player.play()` (or `player.pause()` in the wrap-around case in
`next()`) unless `rememberPause` is true, in which case the play/pause state is left untouched:
```kotlin
override fun next() {
    if (player.repeatMode == Player.REPEAT_MODE_ALL || player.hasNextMediaItem()) {
        player.seekToNext()
        if (!playbackSettings.rememberPause) { player.play() }
    } else {
        player.seekTo(player.currentTimeline.getFirstWindowIndex(player.shuffleModeEnabled), C.TIME_UNSET)
        if (!playbackSettings.rememberPause) { player.pause() }
    }
    ...
}
override fun prev() {
    if (playbackSettings.rewindWithPrev) { player.seekToPrevious() }
    else if (player.hasPreviousMediaItem()) { player.seekToPreviousMediaItem() }
    else { player.seekTo(0) }
    if (!playbackSettings.rememberPause) { player.play() }
    ...
}
// same `if (!playbackSettings.rememberPause) player.play()` guard in goto() and in remove()
// (only when the removed index was the currently-playing one).
```

**Pause on repeat** (`pauseOnRepeat`): implemented purely through media3's
`player.pauseAtEndOfMediaItems` flag, recomputed whenever repeat mode changes or the setting
changes:
```kotlin
override fun repeatMode(repeatMode: RepeatMode) {
    player.repeatMode = ...
    updatePauseOnRepeat()
    ...
}
override fun onPauseOnRepeatChanged() { updatePauseOnRepeat() }  // PlaybackSettings.Listener callback
private fun updatePauseOnRepeat() {
    player.pauseAtEndOfMediaItems =
        player.repeatMode == Player.REPEAT_MODE_ONE && playbackSettings.pauseOnRepeat
}
```
So it only engages when repeat-mode is TRACK (`REPEAT_MODE_ONE`); ExoPlayer itself then pauses at
the end of each single-track loop instead of restarting it. No manual threshold/timer logic needed
— it's a first-class `ExoPlayer` property.

**Rewind before skip back** (`rewindWithPrev`): This is literally whether Auxio calls the
media3-standard `player.seekToPrevious()` (true / default) vs. an always-real-previous-track jump
(false):
```kotlin
override fun prev() {
    if (playbackSettings.rewindWithPrev) {
        player.seekToPrevious()
    } else if (player.hasPreviousMediaItem()) {
        player.seekToPreviousMediaItem()
    } else {
        player.seekTo(0)
    }
    ...
}
```
The actual "rewind if playback position is past N ms, else go to previous track" threshold logic
is **not custom code in Auxio** — it is delegated entirely to media3's built-in
`Player.seekToPrevious()` semantics: media3's default implementation restarts the current item
(seeks to position 0) instead of moving to the previous item when
`player.currentPosition > player.maxSeekToPreviousPosition`. The default value of
`maxSeekToPreviousPosition` in ExoPlayer is **3000 ms (`C.DEFAULT_SEEK_BACK_INCREMENT_MS`-adjacent
constant, actually `ExoPlayer.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS = 3000`)**. Auxio does not
override this value anywhere in the codebase (no `setMaxSeekToPreviousPosition` call found), so it
relies on media3's stock 3-second threshold. When `rewindWithPrev` is turned OFF, Auxio bypasses
that entirely and always jumps to the literal previous queue item (`seekToPreviousMediaItem()`),
or seeks to 0 if there is no previous item.
→ For Sona: replicate by calling `player.seekToPrevious()` when the setting is on (optionally call
`exoPlayer.setSeekParameters`/`setMaxSeekToPreviousPositionMs(3000)` explicitly if you want to be
independent of the default), and `seekToPreviousMediaItem()`/`seekTo(0)` when off.

**Headset autoplay** (`headsetAutoplay`):
File: `app/src/main/java/org/oxycblt/auxio/playback/service/SystemPlaybackReceiver.kt`
A `BroadcastReceiver` registered (via `ContextCompat.registerReceiver(..., RECEIVER_EXPORTED)`)
for `AudioManager.ACTION_HEADSET_PLUG` and `AudioManager.ACTION_AUDIO_BECOMING_NOISY`. Comment
explains why only wired-headset `ACTION_HEADSET_PLUG` is used (Bluetooth equivalents need
BLUETOOTH_CONNECT permission + a prompt, deemed not worth it).
```kotlin
AudioManager.ACTION_HEADSET_PLUG -> {
    when (intent.getIntExtra("state", -1)) {
        0 -> pauseFromHeadsetPlug()
        1 -> playFromHeadsetPlug()
    }
    initialHeadsetPlugEventHandled = true
}
AudioManager.ACTION_AUDIO_BECOMING_NOISY -> pauseFromHeadsetPlug()  // always pauses, unconditional

private fun playFromHeadsetPlug() {
    // Guard: ACTION_HEADSET_PLUG fires once immediately on receiver registration with the
    // current plug state, which would otherwise auto-start playback on app open. Drop that
    // first call using `initialHeadsetPlugEventHandled`.
    if (playbackSettings.headsetAutoplay &&
        playbackManager.currentSong != null &&
        initialHeadsetPlugEventHandled) {
        playbackManager.playing(true)
    }
}
private fun pauseFromHeadsetPlug() {
    if (playbackManager.currentSong != null) playbackManager.playing(false)
}
```
Note: pause-on-unplug (`ACTION_AUDIO_BECOMING_NOISY`) is **not gated by any setting** — it always
pauses if something is loaded. Only the *resume-on-plug-in* behavior is gated by `headsetAutoplay`.

---

## 2. Volume normalization (ReplayGain)

### Settings
File: `app/src/main/java/org/oxycblt/auxio/playback/PlaybackSettings.kt`
```kotlin
val replayGainMode: ReplayGainMode      // key: set_key_replay_gain = "auxio_replay_gain", default DYNAMIC
var replayGainPreAmp: ReplayGainPreAmp  // keys: set_key_pre_amp_with / set_key_pre_amp_without (both Float, default 0f each)
```
`replayGainPreAmp` setter writes both floats atomically via `sharedPreferences.edit { putFloat(...); putFloat(...); apply() }`.

### `ReplayGainMode` enum
File: `app/src/main/java/org/oxycblt/auxio/playback/replaygain/ReplayGainMode.kt`
```kotlin
enum class ReplayGainMode { OFF, TRACK, ALBUM, DYNAMIC }
```
Stored as int code via `IntegerTable`: `REPLAY_GAIN_MODE_OFF=0xA110`, `_TRACK=0xA111`,
`_ALBUM=0xA112`, `_DYNAMIC=0xA113`. Selected via an `IntListPreference` (custom ListPreference
storing an Int, see `settings/ui/IntListPreference.kt`) in `preferences_audio.xml`, default
`@integer/replay_gain_dynamic`.

### `ReplayGainPreAmp`
File: `.../replaygain/ReplayGainPreAmp.kt` — trivial:
```kotlin
data class ReplayGainPreAmp(val with: Float, val without: Float)
```
`with` = extra dB pre-amp applied when RG tags **are** present (added on top of resolved
adjustment); `without` = the dB level used **instead of** any adjustment when RG tags are
**absent** (not additive — it replaces the "0dB adjustment" case entirely). UI:
`replaygain/PreAmpCustomizeDialog.kt`, two `Slider`s bound directly to these fields, "Reset" button
sets both to `0f`. `ReplayGainUtil.kt` has a tiny `Float.formatDb(context)` helper that prefixes
`+`/`-` since Android number formatting won't show a `+` for positive floats.

### ReplayGain tag parsing (musikr module)
File: `musikr/src/main/java/org/oxycblt/musikr/tag/parse/TagFields.kt` (lines ~256-287), operating
over a media3 `Metadata` object already split into `xiph` / `mp4` / `id3v2` maps of frame-id →
`List<String>`:
```kotlin
internal fun Metadata.replayGainTrackAdjustment() =
    (xiph["R128_TRACK_GAIN"]?.parseR128Adjustment()
        ?: xiph["REPLAYGAIN_TRACK_GAIN"]?.parseReplayGainAdjustment()
        ?: mp4["----:COM.APPLE.ITUNES:REPLAYGAIN_TRACK_GAIN"]?.parseReplayGainAdjustment()
        ?: id3v2["TXXX:REPLAYGAIN_TRACK_GAIN"]?.parseReplayGainAdjustment())

internal fun Metadata.replayGainAlbumAdjustment() =
    (xiph["R128_ALBUM_GAIN"]?.parseR128Adjustment()
        ?: xiph["REPLAYGAIN_ALBUM_GAIN"]?.parseReplayGainAdjustment()
        ?: mp4["----:COM.APPLE.ITUNES:REPLAYGAIN_ALBUM_GAIN"]?.parseReplayGainAdjustment()
        ?: id3v2["TXXX:REPLAYGAIN_ALBUM_GAIN"]?.parseReplayGainAdjustment())

private fun List<String>.parseR128Adjustment() =
    first().replace(REPLAYGAIN_ADJUSTMENT_FILTER_REGEX, "").toFloatOrNull()?.run {
        this / 256f + 5   // convert Opus R128 Q7.8 fixed-point gain to LUFS-18-relative dB, matching RG scale
    }

private fun List<String>.parseReplayGainAdjustment() =
    first().replace(REPLAYGAIN_ADJUSTMENT_FILTER_REGEX, "").toFloatOrNull()?.nonZeroOrNull()

private val REPLAYGAIN_ADJUSTMENT_FILTER_REGEX by lazy { Regex("[^\\d.-]") }
```
So it: (1) prefers Opus/Vorbis `R128_TRACK_GAIN`/`R128_ALBUM_GAIN` (integer Q7.8 fixed point,
divide by 256 then +5 to align scale) over classic `REPLAYGAIN_*_GAIN` string tags (e.g.
`"-6.5 dB"`), strips everything except digits/`.`/`-` via regex, parses as Float, and treats an
exact `0f` result as "absent" (`nonZeroOrNull()`) so mode fallback logic (below) still applies. It
also checks the iTunes-style MP4 atom and ID3v2 `TXXX` frames as fallbacks per format.

Result plumbed into `ReplayGainAdjustment(val track: Float?, val album: Float?)`
(`musikr/.../tag/ReplayGainAdjustment.kt`) via `TagInterpreter.kt`, ultimately exposed as
`Song.replayGainAdjustment` (`musikr/.../Music.kt`).

### Applying gain — `ReplayGainAudioProcessor` (media3 `AudioProcessor`)
File: `app/src/main/java/org/oxycblt/auxio/playback/replaygain/ReplayGainAudioProcessor.kt`

Key design note (from the file's own doc comment): rather than using `Player`/`AudioTrack` volume
(which is clamped to `[0,1]` and can't apply **positive** ReplayGain gains), Auxio manipulates the
raw 16-bit PCM sample bytes directly inside a custom `BaseAudioProcessor`.

```kotlin
class ReplayGainAudioProcessor @Inject constructor(
    private val playbackManager: PlaybackStateManager,
    private val playbackSettings: PlaybackSettings,
) : BaseAudioProcessor(), PlaybackStateManager.Listener, PlaybackSettings.Listener {
    private var volume = 1f
        set(value) { field = value; flush() }   // media3 AudioProcessor.flush(): drop buffered audio, force re-process

    fun attach() { playbackManager.addListener(this); playbackSettings.registerListener(this) }
    fun release() { playbackManager.removeListener(this); playbackSettings.unregisterListener(this) }

    override fun onIndexMoved(index: Int) = applyReplayGain(playbackManager.currentSong)
    override fun onQueueChanged(queue, index, change) { if (change.type == SONG) applyReplayGain(...) }
    override fun onNewPlayback(...) = applyReplayGain(playbackManager.currentSong)
    override fun onReplayGainSettingsChanged() = applyReplayGain(playbackManager.currentSong)

    private fun applyReplayGain(song: Song?) {
        if (song == null) { volume = 1f; return }
        val gain = song.replayGainAdjustment
        val preAmp = playbackSettings.replayGainPreAmp
        val resolvedAdjustment = when (playbackSettings.replayGainMode) {
            ReplayGainMode.OFF -> null
            ReplayGainMode.TRACK -> gain.track ?: gain.album
            ReplayGainMode.ALBUM -> gain.album ?: gain.track
            ReplayGainMode.DYNAMIC ->
                gain.album?.takeIf {
                    playbackManager.parent is Album &&
                        playbackManager.currentSong?.album == playbackManager.parent
                } ?: gain.track
        }
        val amplifiedAdjustment =
            if (resolvedAdjustment != null) resolvedAdjustment + preAmp.with
            else preAmp.without
        volume = 10f.pow(amplifiedAdjustment / 20f)   // standard dB -> linear-amplitude conversion
    }

    override fun onConfigure(inputAudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) return inputAudioFormat
        throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        // TODO note in source: media3 only ever feeds AudioProcessors 16-bit PCM today
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val pos = inputBuffer.position(); val limit = inputBuffer.limit()
        val buffer = replaceOutputBuffer(limit - pos)
        if (volume == 1f) {
            buffer.put(inputBuffer.slice())   // no-op fast path; can't use isActive since volume changes live
        } else {
            for (i in pos until limit step 2) {
                var sample = inputBuffer.getLeShort(i)                 // little-endian 16-bit read
                sample = (sample * volume).toInt()
                    .coerceAtLeast(Short.MIN_VALUE.toInt())
                    .coerceAtMost(Short.MAX_VALUE.toInt())
                    .toShort()                                          // clamp to avoid wraparound/pop
                buffer.putLeShort(sample)
            }
        }
        inputBuffer.position(limit); buffer.flip()
    }
}
```
**DYNAMIC mode logic precisely**: use album gain only if the current *playback parent* the user
launched from is literally an `Album` (`playbackManager.parent is Album`) AND that album equals
the current song's own album (`currentSong.album == parent`) — i.e. "I am playing through this
exact album, in album context" — otherwise fall back to track gain. TRACK mode prefers
`gain.track`, falling back to `gain.album` if track gain is absent (and vice versa for ALBUM mode).
OFF disables adjustment entirely (`resolvedAdjustment = null` → `preAmp.without` is used, meaning
even with RG off, the "without-tags" pre-amp is still technically applied! Re-check if that's
desired for Sona, or special-case OFF to always use 0 dB / 1.0 volume).

**Wiring into ExoPlayer** (`playback/service/ExoPlaybackStateHolder.kt`, in the `Factory.create()`
method): the processor is injected straight into a custom `RenderersFactory` that builds only an
audio renderer, via `DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(replayGainProcessor)).build()`:
```kotlin
val audioRenderer = RenderersFactory { handler, _, audioListener, _, _ ->
    arrayOf<BaseRenderer>(
        MediaCodecAudioRenderer(
            context, MediaCodecSelector.DEFAULT, handler, audioListener,
            DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(replayGainProcessor))
                .build(),
        )
    )
}
val exoPlayer = ExoPlayer.Builder(context, audioRenderer)
    .setMediaSourceFactory(mediaSourceFactory)
    .setWakeMode(C.WAKE_MODE_LOCAL)
    .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
    .build()
```
`replayGainProcessor.attach()`/`.release()` are called alongside the holder's own `attach()`/`release()`.

---

## 3. Content/music settings

File: `app/src/main/java/org/oxycblt/auxio/music/MusicSettings.kt`
```kotlin
var separators: String           // key: set_key_separators    = "auxio_separators" (default "")
val intelligentSorting: Boolean  // key: set_key_auto_sort_names = "auxio_auto_sort_names" (default TRUE)
```
(`hideCollaborators` is actually stored/consumed under **HomeSettings**, not MusicSettings — see
below; both preferences live together in the same "Content" screen XML though.)

### Multi-value tag separators
Stored as a raw **string of separator characters** (not a bitmask/int) for extensibility:
`sharedPreferences.getString(key, "") ?: ""`. UI: `music/interpret/SeparatorsDialog.kt`, a checkbox
dialog (comma / semicolon / slash / plus / ampersand) that concatenates checked chars into that
string. Chars are defined as constants in
`musikr/src/main/java/org/oxycblt/musikr/tag/interpret/Separators.kt`:
```kotlin
const val COMMA = ','; const val SEMICOLON = ';'; const val SLASH = '/'; const val PLUS = '+'; const val AND = '&'
```
Splitting logic (`Separators.from(chars)` → `CharSeparators`):
```kotlin
private data class CharSeparators(private val chars: Set<Char>) : Separators {
    override fun split(strings: List<String>) =
        if (strings.size == 1) splitImpl(strings.first()) else strings   // already-multi-valued tag frames pass through untouched
    private fun splitImpl(string: String) =
        string.splitEscaped { chars.contains(it) }.correctWhitespace()
}
```
- If the source tag format already yields multiple string values (e.g. a Vorbis comment with
  repeated `ARTIST=` fields, or ID3v2.4 multi-value frames), **no splitting is performed** — the
  separator setting only kicks in for single delimited strings like `"Artist1;Artist2"`.
- `splitEscaped` (in `musikr/.../util/`, referenced from `Separators.kt`) is a backslash-aware
  split: a separator char preceded by `\` is treated as a literal char instead of a delimiter
  (same escaping convention Auxio also uses for its own `;`-joined location lists in
  `MusicSettings.splitEscaped` — see the near-identical private implementation inlined in
  `MusicSettingsImpl` for location URIs, lines ~260-296 of `MusicSettings.kt`).
- `correctWhitespace()` trims each resulting piece.
- If `separators` is empty, `Separators.from("")` returns a `NoSeparators` singleton (identity
  function, no split at all) — i.e. multi-value splitting is opt-in per character, off by default.

### Intelligent sorting — exact algorithm
File: `musikr/src/main/java/org/oxycblt/musikr/tag/interpret/Naming.kt`
Two `Naming` strategies chosen at index-time based on the `intelligentSorting` setting:
`Naming.intelligent()` → `IntelligentNaming` object, `Naming.simple()` → `SimpleNaming` object.
Both produce a `Name.Known` (see `musikr/.../tag/Name.kt`) holding a `List<Token>` used for
`compareTo`.

`SimpleKnownName` (used when intelligent sorting is OFF): strips punctuation via
`Regex("[\\p{Punct}+]")`, trims, feeds through a locale `Collator` (`Collator.getInstance()` with
`strength = Collator.PRIMARY`, i.e. case/accent-insensitive) to get one single
`LEXICOGRAPHIC`-type `Token`.

`IntelligentKnownName` (used when intelligent sorting is ON) — this is the actual "numeric prefix +
article stripping" algorithm requested:
```kotlin
private fun parseTokens(name: String): List<Token> {
    var stripped = name
        // 1. Replace punctuation with SPACES (not deletion) to force token boundaries —
        //    this is specifically so "15-9" vs "15-10" sort by magnitude of each numeric run
        //    rather than as one glued string.
        .replace(punctRegex /* Regex("[\\p{Punct}+]") */, " ")
        .let { if (it.isBlank()) name else it }   // don't nuke all-punctuation names (e.g. "!!!")
        // 2. Strip a single leading English article, case-insensitively, only if followed by a space
        //    and only if the remaining string is non-trivial:
        .run {
            when {
                length > 4 && startsWith("the ", ignoreCase = true) -> substring(4)
                length > 3 && startsWith("an ", ignoreCase = true)  -> substring(3)
                length > 2 && startsWith("a ", ignoreCase = true)   -> substring(2)
                else -> this
            }
        }

    // 3. On API 29+ (Q), if ICU has an "Any-Latin" transliterator available, transliterate to Latin
    //    script (so e.g. Cyrillic/Greek/Japanese names sort alongside Latin ones).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        Transliterator.getAvailableIDs().toList().contains("Any-Latin")) {
        stripped = Transliterator.getInstance("Any-Latin;").transliterate(stripped)
    }

    // 4. Tokenize into alternating digit-runs and non-digit-runs:
    return TOKEN_REGEX /* Regex("(\\d+)|(\\D+)") */.findAll(stripped).mapTo(mutableListOf()) { match ->
        val token = match.value.trim().ifEmpty { match.value }
        if (token.first().isDigit()) {
            // Strip leading zeros (so "007" and "7" compare equal-ish/by magnitude, not lexicographically)
            val digits = token.trimStart { Character.getNumericValue(it) == 0 }.ifEmpty { token }
            Token(collator.getCollationKey(digits), Token.Type.NUMERIC)
        } else {
            Token(collator.getCollationKey(token), Token.Type.LEXICOGRAPHIC)
        }
    }
}
private companion object { val TOKEN_REGEX = Regex("(\\d+)|(\\D+)") }
```
Comparison (`Token.compareTo`, in `Name.kt`):
```kotlin
override fun compareTo(other: Token): Int {
    val modeComp = type.compareTo(other.type)          // NUMERIC(0) always sorts before LEXICOGRAPHIC(1)
    if (modeComp != 0) return modeComp
    if (type == NUMERIC && collationKey.sourceString.length != other.collationKey.sourceString.length)
        return collationKey.sourceString.length - other.collationKey.sourceString.length   // compare digit-string length first = true numeric magnitude compare
    return collationKey.compareTo(other.collationKey)
}
```
And `Name.Known.compareTo` zips the two token lists and returns the first non-zero token
comparison, falling back to comparing list sizes (so `"Abbey Road"` < `"Abbey Road (Remastered)"`).
`Name.Unknown` (placeholder when no tag exists, one of `Placeholder.ALBUM/ARTIST/GENRE`) always
sorts before any `Known` name.

Summary of "exact algorithm" for Sona's port: **(a)** replace punctuation with spaces, **(b)**
strip one leading article ("the "/"an "/"a ", case-insensitive, length-gated so you don't eat a
1-2 char whole title), **(c)** optionally transliterate to Latin, **(d)** split into alternating
`\d+`/`\D+` runs, **(e)** strip leading zeros off numeric runs, **(f)** compare token-by-token with
numeric tokens always < lexicographic tokens, numeric ties broken by digit-string length first
then collation, lexicographic ties broken by `Collator` (PRIMARY strength — locale-aware,
case/accent-insensitive) comparison.

### Hide collaborators
**Not stored in MusicSettings** — it's in `app/src/main/java/org/oxycblt/auxio/home/HomeSettings.kt`
(it's a *home-tab display* filter, not an indexing rule) even though its UI checkbox lives in the
same "Content" preference screen (`preferences_music.xml`):
```kotlin
val shouldHideCollaborators: Boolean
    get() = sharedPreferences.getBoolean(getString(R.string.set_key_hide_collaborators), false)
    // key: "auxio_hide_collaborators", default false
```
Consumption — `app/src/main/java/org/oxycblt/auxio/home/HomeGenerator.kt`:
```kotlin
override fun artists() =
    musicRepository.library?.let { deviceLibrary ->
        val sorted = listSettings.artistSort.artists(deviceLibrary.artists)
        if (homeSettings.shouldHideCollaborators) {
            sorted.filter { it.explicitAlbums.isNotEmpty() }
        } else {
            sorted
        }
    } ?: emptyList()
```
The underlying model distinction (`musikr/src/main/java/org/oxycblt/musikr/model/ArtistImpl.kt`):
```kotlin
override var explicitAlbums = core.albums                                             // albums where this artist is credited as an ALBUM ARTIST
override var implicitAlbums = core.songs.mapTo(mutableSetOf()) { it.album } - core.albums  // albums the artist only appears on via individual song artist/featured credits
```
So "hide collaborators" = only show artists that are directly credited as the **album artist** of
at least one album; artists who only show up as a featured/track-level artist on other people's
albums (`implicitAlbums` only, `explicitAlbums` empty) are hidden from the Artists tab. This
depends entirely on well-tagged libraries distinguishing ALBUMARTIST from ARTIST tags — hence the
setting's description "works best on well-tagged libraries".

### Settings change propagation
`MusicSettingsImpl.onSettingChanged` dispatches `onIndexingSettingChanged()` for `separators` and
`auto_sort_names` changes (triggers a re-index), while `cover_mode`/`square_covers` changes are
wired at the fragment level (`MusicPreferenceFragment.onSetupPreference`) to call
`musicModel.refresh()` / `imageLoader.memoryCache?.clear()` directly rather than through the
Settings listener mechanism.

---

## 4. Image settings

File: `app/src/main/java/org/oxycblt/auxio/image/ImageSettings.kt`
```kotlin
val coverMode: CoverMode          // key: set_key_cover_mode = "auxio_cover_mode2" (note the "2" — see migration below), default BALANCED
val forceSquareCovers: Boolean    // key: set_key_square_covers = "auxio_square_covers", default false
```
Migration logic in `ImageSettingsImpl.migrate()` handles 3 legacy pref generations (old boolean
"show covers"/"quality covers" flags, and an older `auxio_cover_mode` int without the AS_IS
option, clamping any migrated `HIGH_QUALITY` down to `BALANCED` since "high quality now has space
characteristics that could be undesirable").

### `CoverMode` enum
File: `app/src/main/java/org/oxycblt/auxio/image/CoverMode.kt`
```kotlin
enum class CoverMode { OFF, SAVE_SPACE, BALANCED, HIGH_QUALITY, AS_IS }
```
Int codes via `IntegerTable`: `COVER_MODE_OFF=0xA11C`, `_BALANCED=0xA11D`, `_HIGH_QUALITY=0xA11E`,
`_SAVE_SPACE=0xA125`, `_AS_IS=0xA126`. Selected via `IntListPreference` in
`preferences_music.xml`'s "Images" category, default `@integer/cover_mode_balanced`.

### How each mode affects extraction/caching
File: `app/src/main/java/org/oxycblt/auxio/image/covers/SettingCovers.kt` — this is the piece that
turns the enum into an actual **transcoding strategy** applied when covers are extracted from
audio files and persisted to Auxio's private on-disk cover store (`filesDir/covers/`):
```kotlin
override suspend fun mutate(context: Context, revision: UUID): MutableCovers<out Cover> {
    val coverStorage = CoverStorage.at(context.coversDir())
    val transcoding = when (imageSettings.coverMode) {
        CoverMode.OFF          -> return NullCovers(coverStorage)                       // extraction skipped entirely, no covers stored/shown
        CoverMode.SAVE_SPACE    -> Compress(Bitmap.CompressFormat.JPEG, 500, 70)         // downscale to 500px max dimension, JPEG quality 70
        CoverMode.BALANCED      -> Compress(Bitmap.CompressFormat.JPEG, 750, 85)         // 750px, quality 85 (this is the default)
        CoverMode.HIGH_QUALITY  -> Compress(Bitmap.CompressFormat.JPEG, 1000, 100)       // 1000px, quality 100
        CoverMode.AS_IS         -> NoTranscoding                                        // store the original embedded image bytes verbatim, no re-encode/resize
    }
    val revisionedTranscoding = RevisionedTranscoding(revision, transcoding)  // ties cache entries to a library "revision" UUID so changing mode invalidates old covers
    val storedCovers = MutableStoredCovers(
        EmbeddedCovers(CoverIdentifier.md5()),  // embedded-cover extractor, dedups identical cover art across files via MD5 of the image bytes
        coverStorage,
        revisionedTranscoding,
    )
    val fsCovers = MutableFSCovers(context)      // fallback: folder.jpg / cover.png style files sitting next to the audio files
    return MutableChainedCovers(storedCovers, fsCovers)  // tries embedded-tag cover first, falls back to filesystem image
}
```
So `CoverMode` doesn't just gate a UI quality flag — it's literally the JPEG re-encode
`(maxDimensionPx, quality)` pair used once at index time when persisting extracted cover art to
Auxio's private cache; `AS_IS` skips re-encoding altogether (keeps original bytes/format, e.g. PNG
or higher-res JPEG), and `OFF` skips the whole cover pipeline (`NullCovers`).
Changing this setting triggers a full music library refresh (see `MusicPreferenceFragment.onSetupPreference`
above) since covers need to be re-extracted under the new transcoding.

### Force square covers
File: `app/src/main/java/org/oxycblt/auxio/image/coil/SquareCropTransformation.kt` — a Coil3
`Transformation` (display-time crop, does **not** touch the stored cover bytes/cache from §4
above — it's a separate, load-time step):
```kotlin
class SquareCropTransformation : Transformation() {
    override val cacheKey = "SquareCropTransformation"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val dstSize = min(input.width, input.height)                 // center-crop to the smaller dimension
        val x = (input.width - dstSize) / 2
        val y = (input.height - dstSize) / 2
        val dst = Bitmap.createBitmap(input, x, y, dstSize, dstSize)
        val desiredWidth = size.width.pxOrElse { dstSize }
        val desiredHeight = size.height.pxOrElse { dstSize }
        return if (dstSize != desiredWidth || dstSize != desiredHeight) dst.scale(desiredWidth, desiredHeight) else dst
    }
}
```
When `forceSquareCovers` is on, this transformation is added to the Coil `ImageRequest.transformations`
list wherever album art `ImageView`/composable requests are built (search call-sites of
`ImageSettings.forceSquareCovers` in the UI binder code, e.g. album/song list adapters and the
playback bar/panel cover views — pattern is `if (imageSettings.forceSquareCovers) listOf(SquareCropTransformation.INSTANCE) else emptyList()`).
Toggling the setting doesn't require a re-index (image bytes on disk are untouched) — it just
needs the Coil memory cache cleared so already-loaded (non-square) bitmaps get re-transformed;
that's exactly what `MusicPreferenceFragment.onSetupPreference` does for `set_key_square_covers`:
`imageLoader.memoryCache?.clear()`.

### Image loading stack
- **Coil 3** (`io.coil-kt.coil3:coil-core:3.4.0`, from `app/build.gradle`) is the image loader —
  not Glide.
- DI: `app/src/main/java/org/oxycblt/auxio/image/coil/CoilModule.kt` builds a singleton
  `ImageLoader` with custom `Fetcher`/`Keyer` pairs registered as Coil `components`: a
  `CoverFetcher` (fetches from Auxio's own `Cover` abstraction, not a URL — `Cover.open()` returns
  an `InputStream`-like source wrapped as Coil's `SourceFetchResult`), plus fetchers for
  composited "gallery"/"stack"/"smattering" placeholder images (multi-cover collages used for
  playlists without a single cover, in `GalleryComposeFetcher.kt` / `StackCompositionFetcher.kt` /
  `SmatteringCompositionFetcher.kt`).
- `CoverFetcher.Keyer` cache key = `"${cover.id}&${options.size}"` (size-aware caching).
- Disk caching is explicitly **disabled** (`.diskCachePolicy(CachePolicy.DISABLED)`) — Auxio
  already maintains its own persistent cover store (§ above), so Coil only does in-memory caching
  of decoded/transformed bitmaps.
- Custom crossfade+error-drawable transition via `ErrorCrossfadeTransitionFactory`.

---

## 5. Personalize behavior — remember shuffle

Setting: `PlaybackSettings.keepShuffle` (key `"KEY_KEEP_SHUFFLE"`, default **true**) — despite the
category name "Personalize" in the UI (`PersonalizePreferenceFragment` /
`preferences_personalize.xml`), the flag itself is defined in `PlaybackSettings`, not a
`PersonalizeSettings` class (Auxio has no such class — the personalize screen also hosts the
"home tabs" ordering dialog, unrelated here).

Consumption: `app/src/main/java/org/oxycblt/auxio/playback/state/PlaybackCommand.kt`, in the logic
that decides the `ShuffleMode` for a new-playback command (e.g. tapping a song from a list where
the desired shuffle state isn't explicit):
```kotlin
ShuffleMode.IMPLICIT -> playbackSettings.keepShuffle && playbackManager.isShuffled
```
i.e. when a new playback command doesn't explicitly force shuffle on/off (`ShuffleMode.ON`/`OFF`,
e.g. from a "Shuffle" button), and instead the command's shuffle mode is `IMPLICIT` (default when
just tapping a song), the resolved boolean is: **keep whatever the current shuffle state already
is**, but only if `keepShuffle` is enabled; if `keepShuffle` is false, an `IMPLICIT` command always
resolves to shuffle-off regardless of the current state. This is purely a decision made once at
`PlaybackCommand` construction time (in `PlaybackCommand.Factory`, likely around building
`songFromAll`/`all`/etc. commands) — there is no additional persistence beyond the normal shuffle
state persistence used by `PersistenceRepository`/`ExoPlaybackStateHolder.applySavedState` (queue
shuffle order is serialized as part of `RawQueue`/`toSavedState()`, restored via
`playbackManager.applySavedState`).

Practical effect: with `keepShuffle=true` (default), if you're currently playing on shuffle and
tap an unrelated song in a plain list (an implicit-mode action), the new queue starts shuffled
too. With `keepShuffle=false`, tapping a song always starts that new queue unshuffled unless you
explicitly hit "shuffle".

---

## 6. Gradle / dependency notes for re-implementing in Sona

- **media3 / ExoPlayer**: Auxio does **not** consume media3 as a normal Maven dependency. It
  vendors the *entire* AndroidX Media3 source tree as a local multi-module Gradle include (see
  root `settings.gradle`: `gradle.ext.androidxMediaModulePrefix = 'media-'`,
  `apply from: file("media/core_settings.gradle")`), building modules like
  `media-lib-exoplayer` from source (`app/build.gradle`: `implementation project(":media-lib-exoplayer")`).
  The vendored version is **media3 1.9.1** (`media/constants.gradle`: `releaseVersion = '1.9.1'`).
  This is almost certainly to allow private patches to ExoPlayer internals (e.g. queue/shuffle
  order behavior — see `BetterShuffleOrder` referenced in `ExoPlaybackStateHolder.kt`, a custom
  `ShuffleOrder` implementation, worth a follow-up read if Sona needs identical shuffle
  determinism). **Recommendation for Sona**: unless you need the same low-level ExoPlayer patches,
  just depend on stock `androidx.media3:media3-exoplayer:1.9.1` (+ `media3-common`,
  `media3-session` etc. as needed) from Maven instead of vendoring the source tree — the
  ReplayGain `AudioProcessor`/`DefaultAudioSink.Builder(...).setAudioProcessors(...)` API and
  `Player.seekToPrevious()`/`pauseAtEndOfMediaItems` used above are all stable public media3 APIs.
- **Coil**: `io.coil-kt.coil3:coil-core:3.4.0`.
- No Hilt/DataStore/Room specifics are required to replicate these particular features beyond what
  a normal SharedPreferences + Hilt DI setup provides; Auxio's own persistent song-tag cache is a
  Room database (`musikr/.../cache/db/CacheDatabase.kt`) storing `replayGainTrackAdjustment`/
  `replayGainAlbumAdjustment` columns — relevant only if Sona also wants a fast-rescan tag cache,
  not required just to implement the settings themselves.
