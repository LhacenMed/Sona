package com.lhacenmed.sona.feature.video.controls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import java.util.Date
import kotlinx.coroutines.delay

/** The time and the battery level, kept on screen while the controls are hidden - PLAYit's Display battery level, time. */
@Composable
internal fun BatteryClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            // On the turn of each minute, the only moment the time shown changes.
            delay(MinuteMs - value % MinuteMs)
        }
    }
    val batteryPercent by produceState<Int?>(null) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = intent.batteryPercent()
            }
        }
        // Sticky: registering hands back the level as it is now, before the next change is sent.
        value = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.batteryPercent()
        awaitDispose { context.unregisterReceiver(receiver) }
    }
    Text(
        text = listOfNotNull(DateFormat.getTimeFormat(context).format(Date(now)), batteryPercent?.let { "$it%" })
            .joinToString("  "),
        style = MaterialTheme.typography.labelLarge,
        color = VideoPlayerTokens.ContentColor,
        modifier = modifier,
    )
}

private const val MinuteMs = 60_000L

private fun Intent.batteryPercent(): Int? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else null
}
