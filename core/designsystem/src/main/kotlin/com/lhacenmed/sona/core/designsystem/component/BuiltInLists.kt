package com.lhacenmed.sona.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.BuiltInList
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.builtInList

/** What [this] list is called, in the user's language - everywhere it is named. */
@get:StringRes
val BuiltInList.titleRes: Int
    get() = when (this) {
        BuiltInList.FAVORITES -> R.string.built_in_favorites
        BuiltInList.RECENTLY_PLAYED -> R.string.built_in_recently_played
        BuiltInList.MOST_PLAYED -> R.string.built_in_most_played
    }

/** [titleRes], read. */
val BuiltInList.title: String
    @Composable get() = stringResource(titleRes)

/** [title], where there is little room - a shortcut's card, the player's header. */
val BuiltInList.shortTitle: String
    @Composable get() = stringResource(if (this == BuiltInList.RECENTLY_PLAYED) R.string.built_in_recently_played_short else titleRes)

/** What [this] list is drawn with, wherever it is offered. */
val BuiltInList.icon: ImageVector
    get() = when (this) {
        BuiltInList.FAVORITES -> SonaIcons.Favorite
        BuiltInList.RECENTLY_PLAYED -> SonaIcons.History
        BuiltInList.MOST_PLAYED -> SonaIcons.TrendingUp
    }

/** What [this] list's generated cover is drawn from: one for each, so its row and its screen draw the same in every language. */
val BuiltInList.coverSeed: Int get() = name.hashCode()

/** [this] playlist's name as the app shows it: a built-in one's in the user's language, any other's as the user named it. */
val Playlist.title: String
    @Composable get() = builtInList?.title ?: name
