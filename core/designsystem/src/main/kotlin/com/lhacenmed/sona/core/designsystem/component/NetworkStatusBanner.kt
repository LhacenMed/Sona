@file:OptIn(ExperimentalMaterial3Api::class)

package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.network.NetworkBanner
import com.lhacenmed.sona.core.designsystem.R

/**
 * [content] with the connection banner over it, where ArchiveTune shows it: centred just below the top app
 * bar, above everything the window draws.
 */
@Composable
fun NetworkStatusHost(banner: NetworkBanner, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        NetworkStatusBanner(
            banner = banner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = TopAppBarDefaults.TopAppBarExpandedHeight + 8.dp, start = 16.dp, end = 16.dp),
        )
    }
}

private class NetworkBannerLook(
    val message: String,
    val icon: ImageVector,
    val containerColor: Color,
)

/**
 * That the connection is lost, or back - ArchiveTune's `NetworkStatusBanner`: a pill sliding down into view
 * and back up out of it, red while offline and green once back.
 */
@Composable
private fun NetworkStatusBanner(banner: NetworkBanner, modifier: Modifier = Modifier) {
    // Kept through the exit, so the pill slides away still saying what it said.
    var shownBanner by remember { mutableStateOf<NetworkBanner>(NetworkBanner.Offline) }
    if (banner != NetworkBanner.Hidden) shownBanner = banner

    val look = when (shownBanner) {
        NetworkBanner.Hidden, NetworkBanner.Offline ->
            NetworkBannerLook(stringResource(R.string.network_offline), Icons.Filled.CloudOff, OfflineColor)
        NetworkBanner.BackOnline ->
            NetworkBannerLook(stringResource(R.string.network_back_online), Icons.Filled.CloudDone, BackOnlineColor)
    }

    AnimatedVisibility(
        visible = banner != NetworkBanner.Hidden,
        modifier = modifier,
        enter = slideInVertically(tween(durationMillis = 250)) { -it } + fadeIn(tween(durationMillis = 180)),
        exit = slideOutVertically(tween(durationMillis = 220)) { -it } + fadeOut(tween(durationMillis = 180)),
        label = "networkStatusBanner",
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = look.containerColor,
            contentColor = Color.White,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = look.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = look.message,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

private val OfflineColor = Color(0xFF7F1D1D)
private val BackOnlineColor = Color(0xFF1E8E3E)
