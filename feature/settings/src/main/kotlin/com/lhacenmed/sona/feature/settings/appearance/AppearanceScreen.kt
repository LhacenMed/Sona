package com.lhacenmed.sona.feature.settings.appearance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.CustomFont
import com.lhacenmed.sona.core.datastore.LyricsBackgroundStyle
import com.lhacenmed.sona.core.datastore.MiniPlayerBackgroundStyle
import com.lhacenmed.sona.core.datastore.PlayerBackgroundStyle
import com.lhacenmed.sona.core.datastore.PlayerButtonsStyle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.designsystem.effect.isHighRefreshRate
import com.lhacenmed.sona.core.designsystem.effect.rememberSupportedHighestFps
import com.lhacenmed.sona.core.designsystem.theme.CustomFontLoader
import com.lhacenmed.sona.core.model.AppFont
import com.lhacenmed.sona.core.model.ThemeMode
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import kotlin.math.roundToInt

/** Whether this device has wallpaper colours to offer: Material You arrived with Android 12. */
private val hasWallpaperColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The player backgrounds this device can draw: blurring needs Android 12, so the plain blur is only
 * offered from there - as ArchiveTune offers it.
 */
private val playerBackgroundOptions = PlayerBackgroundStyle.entries.filter {
    it != PlayerBackgroundStyle.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/** The lyrics backgrounds on offer - the custom one is the player's, followed rather than chosen. */
private val lyricsBackgroundOptions = listOf(
    LyricsBackgroundStyle.DEFAULT,
    LyricsBackgroundStyle.FOLLOW_THEME,
    LyricsBackgroundStyle.COLORING,
)

/** How the app looks: its theme and colours, its typeface, the player, and how it moves. */
data object AppearanceScreen : Screen {
    override val titleRes: Int get() = R.string.appearance_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current
        val viewModel: AppearanceSettingsViewModel = hiltViewModel()
        val theme by viewModel.theme.collectAsStateWithLifecycle()
        val player by viewModel.player.collectAsStateWithLifecycle()
        val chosenLyricsBackground by viewModel.lyricsBackground.collectAsStateWithLifecycle()
        val roundMode by viewModel.roundMode.collectAsStateWithLifecycle()
        val disableAnimations by viewModel.disableAnimations.collectAsStateWithLifecycle()
        val forceHighRefreshRate by viewModel.forceHighRefreshRate.collectAsStateWithLifecycle()
        var showSeekBarStyleDialog by rememberSaveable { mutableStateOf(false) }

        val isDarkTheme = theme.mode.isDark()
        // What each colour switch stands for right now: dynamic colours are both at once, and hold both.
        val usesWallpaperColors = hasWallpaperColors && (theme.dynamicColors || theme.wallpaperColors)
        val usesCoverColors = theme.dynamicColors || theme.coverColors
        val lyricsBackground = chosenLyricsBackground.resolveFor(player.background)

        val customFontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            pickCustomFont(context, uri, previousUri = theme.customFont?.uri)?.let(viewModel::setCustomFont)
        }
        val pickFont = { customFontPicker.launch(CustomFontLoader.supportedMimeTypes) }

        SettingsList {
            SettingsSection(stringResource(R.string.appearance_theme_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.theme_title),
                    options = ThemeMode.entries.map { themeModeLabel(it) },
                    selectedIndex = theme.mode.ordinal,
                    onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                )
                // ArchiveTune's: only a dark theme has surfaces to turn black.
                if (isDarkTheme) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.black_theme_title),
                        summary = stringResource(R.string.black_theme_summary),
                        checked = theme.pureBlack,
                        onCheckedChange = viewModel::setPureBlack,
                    )
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.dynamic_colors_title),
                    summary = stringResource(R.string.dynamic_colors_summary),
                    checked = theme.dynamicColors,
                    onCheckedChange = viewModel::setDynamicColors,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.wallpaper_colors_title),
                    summary = stringResource(
                        if (hasWallpaperColors) R.string.wallpaper_colors_summary else R.string.wallpaper_colors_unavailable,
                    ),
                    checked = usesWallpaperColors,
                    onCheckedChange = viewModel::setWallpaperColors,
                    enabled = hasWallpaperColors && !theme.dynamicColors,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.cover_colors_title),
                    summary = stringResource(R.string.cover_colors_summary),
                    checked = usesCoverColors,
                    onCheckedChange = viewModel::setCoverColors,
                    enabled = !theme.dynamicColors,
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.color_palette_title),
                    summary = colorPaletteLabel(theme.colorPalette),
                    onClick = { navigator.go(ColorPaletteScreen) },
                    enabled = !theme.dynamicColors && !usesWallpaperColors,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.app_icon_title),
                    options = listOf(
                        stringResource(R.string.app_icon_default),
                        stringResource(R.string.app_icon_monochrome),
                    ),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.font_title),
                    options = AppFont.entries.map { fontLabel(it) },
                    selectedIndex = theme.font.ordinal,
                    onSelect = { index ->
                        val font = AppFont.entries[index]
                        // The custom font becomes the font once one is picked, so it is never custom without one.
                        if (font == AppFont.CUSTOM && theme.customFont == null) pickFont() else viewModel.setFont(font)
                    },
                )
                if (theme.font == AppFont.CUSTOM) {
                    SettingsNavigationItem(
                        title = stringResource(R.string.custom_font_title),
                        summary = theme.customFont?.name?.ifBlank { null } ?: stringResource(R.string.custom_font_summary),
                        onClick = pickFont,
                    )
                }
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.appearance_player_section)) {
                // Listed straight from PlayerStyle, so an option's index is the style it names and a new
                // style is offered here the moment it exists.
                SettingsChoiceItem(
                    title = stringResource(R.string.player_style_title),
                    options = PlayerStyle.entries.map { playerStyleLabel(it) },
                    selectedIndex = player.style.ordinal,
                    onSelect = { viewModel.setPlayerStyle(PlayerStyle.entries[it]) },
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.player_slider_style_title),
                    summary = seekBarStyleLabel(player.sliderStyle),
                    onClick = { showSeekBarStyleDialog = true },
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.player_background_title),
                    options = playerBackgroundOptions.map { playerBackgroundLabel(it) },
                    selectedIndex = playerBackgroundOptions.indexOf(player.background).coerceAtLeast(0),
                    onSelect = { viewModel.setPlayerBackground(playerBackgroundOptions[it]) },
                )
                if (player.background == PlayerBackgroundStyle.CUSTOM) {
                    SettingsNavigationItem(
                        title = stringResource(R.string.customized_background_title),
                        summary = stringResource(R.string.customized_background_summary),
                        onClick = { navigator.go(CustomBackgroundScreen) },
                    )
                }
                // Shows the player's custom image while the player has one, and waits for it to go.
                SettingsChoiceItem(
                    title = stringResource(R.string.lyrics_background_style_title),
                    options = lyricsBackgroundOptions.map { lyricsBackgroundLabel(it) },
                    selectedIndex = lyricsBackgroundOptions.indexOf(chosenLyricsBackground).coerceAtLeast(0),
                    onSelect = { viewModel.setLyricsBackground(lyricsBackgroundOptions[it]) },
                    summary = lyricsBackgroundLabel(lyricsBackground),
                    enabled = player.background != PlayerBackgroundStyle.CUSTOM,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.mini_player_background_title),
                    options = MiniPlayerBackgroundStyle.entries.map { miniPlayerBackgroundLabel(it) },
                    selectedIndex = player.miniPlayerBackground.ordinal,
                    onSelect = { viewModel.setMiniPlayerBackground(MiniPlayerBackgroundStyle.entries[it]) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.hide_player_thumbnail_title),
                    summary = stringResource(R.string.hide_player_thumbnail_summary),
                    checked = player.hideThumbnail,
                    onCheckedChange = viewModel::setHideThumbnail,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.player_buttons_title),
                    options = PlayerButtonsStyle.entries.map { playerButtonsLabel(it) },
                    selectedIndex = player.buttonsStyle.ordinal,
                    onSelect = { viewModel.setButtonsStyle(PlayerButtonsStyle.entries[it]) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.swipe_to_change_song_title),
                    summary = stringResource(R.string.swipe_to_change_song_summary),
                    checked = player.swipeToChangeTrack,
                    onCheckedChange = viewModel::setSwipeToChangeTrack,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.appearance_motion_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.round_mode_title),
                    summary = stringResource(R.string.round_mode_summary),
                    checked = roundMode,
                    onCheckedChange = viewModel::setRoundMode,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.disable_animations_title),
                    summary = stringResource(R.string.disable_animations_summary),
                    checked = disableAnimations,
                    onCheckedChange = viewModel::setDisableAnimations,
                )
                // ArchiveTune's row: the fastest rate this display offers, and nothing to force on a
                // display with none above the standard one.
                val supportedHighestFps = rememberSupportedHighestFps()
                SettingsSwitchItem(
                    title = stringResource(R.string.high_refresh_rate_title),
                    summary = stringResource(R.string.high_refresh_rate_summary, supportedHighestFps.roundToInt()),
                    checked = forceHighRefreshRate,
                    onCheckedChange = viewModel::setForceHighRefreshRate,
                    enabled = isHighRefreshRate(supportedHighestFps),
                )
            }
        }

        if (showSeekBarStyleDialog) {
            SeekBarStyleDialog(
                selectedStyle = player.sliderStyle,
                onSelect = viewModel::setSliderStyle,
                onDismiss = { showSeekBarStyleDialog = false },
            )
        }
    }
}

/** Whether the app is drawn dark under this mode - as `SonaTheme` decides it. */
@Composable
internal fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

/**
 * The font at [uri], kept readable across restarts, and the grant on [previousUri] let go - or null, with
 * the user told why, when it is not a `.ttf`. ArchiveTune's custom font picker.
 */
private fun pickCustomFont(context: Context, uri: Uri, previousUri: String?): CustomFont? {
    if (!CustomFontLoader.isSupportedTtf(context, uri)) {
        Toast.makeText(context, R.string.custom_font_invalid, Toast.LENGTH_SHORT).show()
        return null
    }
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    if (previousUri != null && previousUri != uri.toString()) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(previousUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    return CustomFont(uri = uri.toString(), name = CustomFontLoader.displayName(context, uri))
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    stringResource(
        when (mode) {
            ThemeMode.SYSTEM -> R.string.theme_system
            ThemeMode.LIGHT -> R.string.theme_light
            ThemeMode.DARK -> R.string.theme_dark
        },
    )

@Composable
private fun fontLabel(font: AppFont): String =
    stringResource(
        when (font) {
            AppFont.DEFAULT -> R.string.font_default
            AppFont.SYSTEM -> R.string.font_system
            AppFont.INTER -> R.string.font_inter
            AppFont.CUSTOM -> R.string.font_custom
        },
    )

@Composable
private fun playerStyleLabel(style: PlayerStyle): String =
    when (style) {
        PlayerStyle.DEFAULT -> stringResource(R.string.player_style_default)
    }

@Composable
private fun playerBackgroundLabel(style: PlayerBackgroundStyle): String =
    stringResource(
        when (style) {
            PlayerBackgroundStyle.DEFAULT -> R.string.background_follow_theme
            PlayerBackgroundStyle.GRADIENT -> R.string.background_gradient
            PlayerBackgroundStyle.CUSTOM -> R.string.background_custom
            PlayerBackgroundStyle.BLUR -> R.string.background_blur
            PlayerBackgroundStyle.COLORING -> R.string.background_coloring
            PlayerBackgroundStyle.BLUR_GRADIENT -> R.string.background_blur_gradient
            PlayerBackgroundStyle.GLOW -> R.string.background_glow
            PlayerBackgroundStyle.GLOW_ANIMATED -> R.string.background_glow_animated
        },
    )

@Composable
private fun lyricsBackgroundLabel(style: LyricsBackgroundStyle): String =
    stringResource(
        when (style) {
            LyricsBackgroundStyle.DEFAULT -> R.string.lyrics_background_default
            LyricsBackgroundStyle.FOLLOW_THEME -> R.string.background_follow_theme
            LyricsBackgroundStyle.COLORING -> R.string.background_coloring
            LyricsBackgroundStyle.CUSTOM -> R.string.background_custom
        },
    )

@Composable
private fun miniPlayerBackgroundLabel(style: MiniPlayerBackgroundStyle): String =
    stringResource(
        when (style) {
            MiniPlayerBackgroundStyle.THEME -> R.string.background_follow_theme
            MiniPlayerBackgroundStyle.GRADIENT -> R.string.background_gradient
            MiniPlayerBackgroundStyle.GLOW -> R.string.background_glow
        },
    )

@Composable
private fun playerButtonsLabel(style: PlayerButtonsStyle): String =
    stringResource(
        when (style) {
            PlayerButtonsStyle.DEFAULT -> R.string.player_buttons_default
            PlayerButtonsStyle.SECONDARY -> R.string.player_buttons_secondary
        },
    )
