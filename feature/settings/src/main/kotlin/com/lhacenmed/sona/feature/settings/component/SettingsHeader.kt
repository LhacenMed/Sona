package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Who a settings screen is about, over its first section - About's app, with its [mark] over its [title]
 * and a line of [details] under it. The mark fills a square [HeaderMarkSize] across. Centred and bare, an identity rather than a [SettingsHero]'s card of
 * state and actions, and no taller than it needs to be, so it heads the rows rather than competing with them.
 */
@Composable
fun SettingsHeader(mark: @Composable () -> Unit, title: String, details: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(HeaderMarkSize)) { mark() }
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = details,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** How big a header's mark is: Sona's, as it stands on the launch screen. */
private val HeaderMarkSize = 96.dp
