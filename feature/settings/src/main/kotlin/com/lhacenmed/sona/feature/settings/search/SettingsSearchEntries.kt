package com.lhacenmed.sona.feature.settings.search

import androidx.annotation.StringRes
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.SettingsCategory
import com.lhacenmed.sona.feature.settings.screen.LyricsAnimationScreen
import com.lhacenmed.sona.feature.settings.screen.purposeRes
import com.lhacenmed.sona.feature.settings.screen.titleRes

/**
 * One setting a search can find: the row titled [titleRes] - [summaryRes] under it, where it has one of its own - in
 * the section [sectionRes] of [screen], one of [category]'s. A search opens [screen] scrolled to it, by its title - see
 * [SettingsRowScreen][com.lhacenmed.sona.feature.settings.SettingsRowScreen].
 */
internal class SettingsSearchEntry(
    val category: SettingsCategory,
    @StringRes val sectionRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int?,
    val screen: Screen,
)

/** A row as its section lists it - on its category's own screen, unless another [screen] of the category's holds it. */
private class SectionRow(@StringRes val titleRes: Int, @StringRes val summaryRes: Int?, val screen: Screen?)

private fun row(@StringRes titleRes: Int, @StringRes summaryRes: Int? = null, screen: Screen? = null) =
    SectionRow(titleRes, summaryRes, screen)

/** The [rows] of [this] category's section [sectionRes], in the order the section lists them. */
private fun SettingsCategory.section(@StringRes sectionRes: Int, vararg rows: SectionRow): List<SettingsSearchEntry> =
    rows.map { SettingsSearchEntry(this, sectionRes, it.titleRes, it.summaryRes, it.screen ?: screen) }

/**
 * Every row a settings search finds, by the category and the section it is in - the rows of each screen, in the order
 * the screen lists them. A row added to a settings screen is added here too, to be found - `SettingsSearchEntriesTest`
 * names any that is not; the row itself needs nothing more, as every settings row can be arrived at by its title.
 *
 * Rows that only report - the listening stats' figures, a dialog's choices - are left out: they are nothing to set.
 */
internal val SettingsSearchEntries: List<SettingsSearchEntry> = with(SettingsCategory.Appearance) {
    section(
        R.string.appearance_theme_section,
        row(R.string.theme_title),
        row(R.string.black_theme_title, R.string.black_theme_summary),
        row(R.string.dynamic_colors_title, R.string.dynamic_colors_summary),
        row(R.string.wallpaper_colors_title),
        row(R.string.cover_colors_title, R.string.cover_colors_summary),
        row(R.string.color_palette_title),
        row(R.string.app_icon_title),
        row(R.string.font_title),
        row(R.string.custom_font_title),
    ) + section(
        R.string.appearance_player_section,
        row(R.string.player_style_title),
        row(R.string.player_slider_style_title),
        row(R.string.player_background_title),
        row(R.string.customized_background_title, R.string.customized_background_summary),
        row(R.string.mini_player_background_title),
        row(R.string.hide_player_thumbnail_title, R.string.hide_player_thumbnail_summary),
        row(R.string.swipe_to_change_song_title, R.string.swipe_to_change_song_summary),
    ) + section(
        R.string.appearance_motion_section,
        row(R.string.round_mode_title, R.string.round_mode_summary),
        row(R.string.disable_animations_title, R.string.disable_animations_summary),
        row(R.string.high_refresh_rate_title, R.string.high_refresh_rate_summary),
    )
} + with(SettingsCategory.Playback) {
    section(
        R.string.playback_controls_section,
        row(R.string.rewind_before_skip_title, R.string.rewind_before_skip_summary),
        row(R.string.seek_amount_title),
        row(R.string.stop_after_current_option_title, R.string.stop_after_current_option_summary),
        row(R.string.remember_pause_title, R.string.remember_pause_summary),
    ) + section(
        R.string.playback_queue_section,
        row(R.string.persisted_playback_notification_title, R.string.persisted_playback_notification_summary),
        row(R.string.persistent_queue_title, R.string.persistent_queue_summary),
        row(R.string.skip_on_error_title, R.string.skip_on_error_summary),
        row(R.string.stop_on_task_clear_title, R.string.stop_on_task_clear_summary),
    ) + section(
        R.string.playback_history_section,
        row(R.string.uncounted_collections_title, R.string.uncounted_collections_summary),
        row(R.string.counts_videos_in_most_played_title, R.string.counts_videos_in_most_played_summary),
    ) + section(
        R.string.playback_shuffle_section,
        row(R.string.keep_shuffle_title, R.string.keep_shuffle_summary),
        row(R.string.reshuffle_each_time_title, R.string.reshuffle_each_time_summary),
        row(R.string.remember_shuffle_order_title, R.string.remember_shuffle_order_summary),
    ) + section(
        R.string.playback_quick_play_section,
        row(R.string.quick_play_button_title, R.string.quick_play_button_summary),
        row(R.string.quick_play_mode_title),
        row(R.string.quick_play_source_title),
    ) + section(
        R.string.playback_transitions_section,
        row(R.string.crossfade_title),
        row(R.string.gapless_title, R.string.gapless_summary),
        row(R.string.skip_silence_title, R.string.skip_silence_summary),
    ) + section(
        R.string.playback_audio_section,
        row(R.string.equalizer_settings_title, R.string.equalizer_settings_summary),
        row(R.string.audio_offload_title, R.string.audio_offload_summary),
        row(R.string.pause_on_mute_title, R.string.pause_on_mute_summary),
        row(R.string.replaygain_strategy_title),
        row(R.string.replaygain_preamp_title),
    ) + section(
        R.string.playback_headphones_section,
        row(R.string.headset_autoplay_title, R.string.headset_autoplay_summary),
        row(R.string.bluetooth_autoplay_title, R.string.bluetooth_autoplay_summary),
        row(R.string.swap_headphone_buttons_title, R.string.swap_headphone_buttons_summary),
    )
} + with(SettingsCategory.Video) {
    section(
        R.string.video_display_section,
        row(R.string.video_scan_list_title, R.string.video_scan_list_summary),
        row(R.string.video_keep_aspect_title, R.string.video_keep_aspect_summary),
        row(R.string.video_show_clock_title, R.string.video_show_clock_summary),
    ) + section(
        R.string.video_playback_section,
        row(R.string.video_subtitle_customization_title, R.string.video_subtitle_customization_summary),
        row(R.string.video_subtitle_text_size_title),
        row(R.string.video_subtitle_backdrop_title),
        row(R.string.video_subtitle_position_title),
        row(R.string.video_resume_title, R.string.video_resume_summary),
        row(R.string.video_gestures_title, R.string.video_gestures_summary),
        row(R.string.video_auto_popup_title, R.string.video_auto_popup_summary),
        row(R.string.video_double_tap_seek_title, R.string.video_double_tap_seek_summary),
        row(R.string.video_double_tap_seek_time_title),
        row(R.string.video_long_press_speed_up_title, R.string.video_long_press_speed_up_summary),
        row(R.string.video_long_press_speed_title),
        row(R.string.video_long_press_vibration_title, R.string.video_long_press_vibration_summary),
        row(R.string.video_zoom_pan_title, R.string.video_zoom_pan_summary),
    )
} + with(SettingsCategory.Lyrics) {
    section(
        R.string.lyrics_display_section,
        row(R.string.lyrics_animation_style_title, R.string.lyrics_animation_style_summary),
        row(R.string.lyrics_click_change_title, R.string.lyrics_click_change_summary),
        row(R.string.lyrics_auto_scroll_title, R.string.lyrics_auto_scroll_summary),
        row(R.string.lyrics_blur_title, R.string.lyrics_blur_summary),
        row(R.string.lyrics_text_size_title),
        row(R.string.lyrics_line_spacing_title),
        row(R.string.lyrics_show_player_controls_title, R.string.lyrics_show_player_controls_summary),
    ) + section(
        R.string.lyrics_romanization_section,
        row(R.string.romanize_arabic_title, R.string.romanize_arabic_summary),
        row(R.string.romanize_japanese_title, R.string.romanize_japanese_summary),
        row(R.string.romanize_korean_title, R.string.romanize_korean_summary),
        row(R.string.romanize_chinese_title, R.string.romanize_chinese_summary),
        row(R.string.romanize_hindi_title, R.string.romanize_hindi_summary),
        row(R.string.romanize_other_languages_title, R.string.romanize_other_languages_summary),
        row(R.string.lyrics_romanization_only_title, R.string.lyrics_romanization_only_summary),
    ) + section(
        R.string.lyrics_online_section,
        row(R.string.lyrics_find_missing_title, R.string.lyrics_find_missing_summary),
    ) + section(
        R.string.lyrics_animation_tuning_section,
        row(R.string.lyrics_line_bounce_title, R.string.lyrics_line_bounce_summary, LyricsAnimationScreen),
        row(R.string.lyrics_bounce_amplitude_title, screen = LyricsAnimationScreen),
        row(R.string.lyrics_glow_intensity_title, screen = LyricsAnimationScreen),
        row(R.string.lyrics_fill_smoothness_title, screen = LyricsAnimationScreen),
    )
} + with(SettingsCategory.Content) {
    section(
        R.string.content_library_section,
        row(R.string.automatic_reloading_title, R.string.automatic_reloading_summary),
        row(R.string.excluded_folders_title, R.string.excluded_folders_summary),
        row(R.string.auto_tag_title, R.string.auto_tag_summary),
        row(R.string.folder_view_title),
    ) + section(
        R.string.content_tags_section,
        row(R.string.separate_artists_title, R.string.separate_artists_summary),
        row(R.string.intelligent_sorting_title, R.string.intelligent_sorting_summary),
        row(R.string.hide_collaborators_title, R.string.hide_collaborators_summary),
    ) + section(
        R.string.content_sorting_section,
        row(R.string.sort_new_tracks_title),
        row(R.string.sort_default_scope_title),
        row(R.string.sort_reset_title, R.string.sort_reset_summary),
    ) + section(
        R.string.content_images_section,
        row(R.string.album_covers_title),
        row(R.string.force_square_covers_title, R.string.force_square_covers_summary),
    ) + section(
        R.string.content_language_section,
        row(R.string.use_system_language_title, R.string.use_system_language_summary),
        row(R.string.app_language_title),
    )
} + with(SettingsCategory.Behavior) {
    section(
        R.string.behavior_display_section,
        row(R.string.library_tabs_title, R.string.library_tabs_summary),
        row(R.string.playback_bar_action_title),
        row(R.string.fast_scroll_touch_area_title),
        row(R.string.pull_to_refresh_title, R.string.pull_to_refresh_summary),
    ) + section(
        R.string.behavior_playing_section,
        row(R.string.play_from_library_title),
        row(R.string.play_from_details_title),
    ) + section(
        R.string.behavior_history_section,
        row(R.string.pause_listen_history_title, R.string.pause_listen_history_summary),
        row(R.string.clear_listen_history_title, R.string.clear_listen_history_summary),
    ) + section(
        R.string.behavior_privacy_section,
        row(R.string.haptics_title, R.string.haptics_summary),
        row(R.string.block_screenshots_title, R.string.block_screenshots_summary),
    )
} + with(SettingsCategory.ListeningStats) {
    section(R.string.stats_period_section, row(R.string.stats_period_title))
} + with(SettingsCategory.Permissions) {
    // The very permissions the screen lists, which are those this device asks for.
    section(R.string.permissions_section, *AppPermission.needed.map { row(it.titleRes, it.purposeRes) }.toTypedArray())
} + with(SettingsCategory.Storage) {
    section(
        R.string.storage_library_section,
        row(R.string.storage_database_used_title),
        row(R.string.storage_rescan_title),
    ) + section(
        R.string.storage_image_cache_section,
        row(R.string.storage_image_cache_used_title),
        row(R.string.storage_clear_image_cache_title, R.string.storage_clear_image_cache_summary),
    )
} + with(SettingsCategory.Backup) {
    section(
        R.string.backup_section,
        row(R.string.backup_now_title),
        row(R.string.backup_schedule_title),
        row(R.string.backup_encrypt_title),
        row(R.string.backup_drive_title),
        row(R.string.backup_folders_title),
        row(R.string.backup_mobile_data_title),
    ) + section(
        R.string.restore_section,
        row(R.string.restore_from_file_title),
    )
} + with(SettingsCategory.Updates) {
    section(
        R.string.updates_preferences_section,
        row(R.string.updates_channel_title),
        row(R.string.updates_auto_prompt_title, R.string.updates_auto_prompt_summary),
        row(R.string.updates_notifications_title, R.string.updates_notifications_summary),
    ) + section(
        R.string.updates_development_section,
        row(R.string.updates_recent_commits, R.string.updates_commits_summary),
    )
} + with(SettingsCategory.About) {
    section(R.string.about_project, row(R.string.about_licenses, R.string.about_licenses_summary))
}
