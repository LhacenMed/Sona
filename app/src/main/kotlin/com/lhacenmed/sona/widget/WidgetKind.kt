package com.lhacenmed.sona.widget

import androidx.annotation.LayoutRes
import com.lhacenmed.sona.R

/**
 * The widgets the app puts on the home screen - the one list of them [PlaybackWidgets] draws. Each is its own
 * provider, so the launcher lists it apart, drawn in its own layout from the views the widgets share: a view a
 * layout lacks is simply not drawn, so a widget has exactly the parts its layout gives it.
 *
 * A new widget is one more of these, with what the platform asks of each widget on its own: a provider - see
 * [PlaybackWidgetProvider] - a layout, the provider info describing its size, and its receiver in the manifest.
 */
internal enum class WidgetKind(
    val provider: Class<out PlaybackWidgetProvider>,
    @LayoutRes val layoutRes: Int,
    /** Whether it shows the timeline, which is drawn again on its own each second it moves by. */
    val hasTimeline: Boolean,
    /** How round its cover's corners are, as a share of its side - half of it is a circle. */
    val coverCornerFraction: Float,
) {
    NOW_PLAYING(NowPlayingWidgetProvider::class.java, R.layout.widget_now_playing, hasTimeline = true, coverCornerFraction = 0.07f),
    MINI_PLAYER(MiniPlayerWidgetProvider::class.java, R.layout.widget_mini_player, hasTimeline = false, coverCornerFraction = 0.5f),
}
