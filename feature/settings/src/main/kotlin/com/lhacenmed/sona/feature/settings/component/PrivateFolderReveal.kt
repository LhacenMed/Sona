package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.overscroll.ArmedTriggerReveal
import com.lhacenmed.sona.core.designsystem.component.overscroll.OverscrollEdge
import com.lhacenmed.sona.core.designsystem.component.overscroll.OverscrollTrigger
import com.lhacenmed.sona.core.designsystem.motion.RubberBandOverscroll

/** How far past the list's top a pull must go before letting go opens the Private Folder. */
private val RevealThreshold = 130.dp

/**
 * The hidden "Private Folder" entry pulled down from above the Settings list - see `SettingsList`'s
 * `onPrivateFolderRevealed`: an [OverscrollTrigger] past the list's top. Nothing shows while the list is
 * merely stretched - only once the pull is past [RevealThreshold] does the entry slide down into view, so
 * letting go opens it, and slide back away if the pull falls short again.
 *
 * Deliberately not a normal, always-visible settings row: the Private Folder is reached only this way.
 */
@Composable
internal fun BoxScope.PrivateFolderOverscrollReveal(overscroll: RubberBandOverscroll, onRevealed: () -> Unit) {
    OverscrollTrigger(
        overscroll = overscroll,
        edge = OverscrollEdge.Start,
        threshold = RevealThreshold,
        onTrigger = { onRevealed() },
    ) { reveal ->
        ArmedTriggerReveal(reveal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text("Private Folder", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
