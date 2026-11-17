package com.lhacenmed.sona.feature.update

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.lifecycle.isAppInForeground
import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.Releases
import com.lhacenmed.sona.feature.update.notification.UpdateNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps [UpdateRegistry] current for as long as the app is open - the one place the app looks for updates
 * while it is in use, whatever screen or activity is showing.
 *
 * Started once, by the application ([start]):
 *
 *  - It puts back what the last session left: a downloaded APK newer than the running build resumes straight
 *    to [UpdateState.Downloaded] (the install prompt); otherwise the newest kept release on the channel is
 *    offered again while it is newer, offline too. The channel changing re-reads what was kept at once.
 *  - While the app is in the foreground and online, it asks GitHub - at once, then every
 *    [RecheckIntervalMillis]. The first ask of each process goes to GitHub whatever was kept, so an app
 *    opened just after a release finds it; later ones come from what was kept while that is recent - see
 *    `CachedGitHubResource`. An ask GitHub does not answer - a connection that reads as online and is not,
 *    a timeout, a refusal - is asked again after [RetryDelaysMillis], and at once when the connection comes
 *    back, the channel changes or the app returns to the foreground.
 *
 * Whatever it finds lands in [UpdateRegistry], which every activity's prompt follows - see
 * [UpdateGate][com.lhacenmed.sona.feature.update.ui.UpdateGate].
 *
 * It is also where the settings meet the work done while the app is closed - see [UpdateWork]: the background
 * check runs while the app updates itself or announces updates, and an update found is delivered by itself
 * while that is turned on - see [AutoUpdater] - and installed as the app leaves the screen.
 */
@Singleton
class UpdateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val networkMonitor: NetworkMonitor,
    private val updateSettings: UpdateSettings,
) {
    /** One ask at a time, so a check asked for on the Updates screen never races the monitor's own. */
    private val checkMutex = Mutex()

    /** Whether GitHub has answered an ask since the process started. */
    @Volatile
    private var hasAnswered = false

    private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true
        scope.launch(Dispatchers.IO) {
            // A debug build is never offered an update, so it has nothing to look for.
            if (!context.installedBuild().isUpdatable) return@launch
            restore(updateSettings.channel.value)
            launch { updateSettings.channel.flow.drop(1).collect { offerKept(it) } }
            launch {
                combine(updateSettings.autoUpdate.flow, updateSettings.notifications.flow) { updates, notifies -> updates || notifies }
                    .distinctUntilChanged()
                    .collect { UpdateWork.followChecks(context, it) }
            }
            launch { updateSettings.notifications.flow.collect { notifies -> if (!notifies) UpdateNotifier.dismiss(context) } }
            launch {
                updateSettings.autoUpdate.flow.collect { updates -> if (updates) deliverAvailable() else UpdateWork.cancelDelivery(context) }
            }
            // A connection newly allowed or refused is one the delivery starts again to follow.
            launch { updateSettings.usesMobileData.flow.drop(1).collect { deliverAvailable(replacing = true) } }
            launch { isAppInForeground().collectLatest { isForeground -> if (!isForeground) installStaged() } }
            combine(isAppInForeground(), networkMonitor.isOnline, updateSettings.channel.flow) { isForeground, isOnline, channel ->
                channel.takeIf { isForeground && isOnline }
            }
                .distinctUntilChanged()
                .collectLatest { channel -> if (channel != null) keepChecking(channel) }
        }
    }

    /** Asks GitHub now, whatever was kept - the Updates screen's Check for update. */
    suspend fun checkNow(): Result<Release> = check(updateSettings.channel.value, forceRefresh = true)

    private suspend fun check(channel: UpdateChannel, forceRefresh: Boolean): Result<Release> =
        checkMutex.withLock {
            UpdateChecker.check(context, channel, forceRefresh).onSuccess {
                hasAnswered = true
                deliverAvailable()
            }
        }

    /** Has the available update delivered by itself, while the app updates itself and there is one. */
    private fun deliverAvailable(replacing: Boolean = false) {
        if (updateSettings.autoUpdate.value && UpdateRegistry.available.value != null) {
            UpdateWork.deliver(context, updateSettings.usesMobileData.value, replacing)
        }
    }

    /**
     * Installs the update already downloaded, now that the app has left the screen - once it has stayed away
     * [LeaveGraceMillis], so a glance at another app is not taken for leaving. Still busy - playing, say - it
     * is left to [UpdateDeliveryWorker], which comes back to it.
     */
    private suspend fun installStaged() {
        delay(LeaveGraceMillis)
        val staged = (UpdateRegistry.stateOf() as? UpdateState.Downloaded)?.staged ?: return
        if (updateSettings.autoUpdate.value) AutoUpdater.install(context, staged)
    }

    /** Asks until GitHub answers, backing off between failures, then again every [RecheckIntervalMillis]. */
    private suspend fun keepChecking(channel: UpdateChannel) {
        var failures = 0
        while (true) {
            val isAnswered = check(channel, forceRefresh = !hasAnswered).isSuccess
            val wait = if (isAnswered) {
                failures = 0
                RecheckIntervalMillis
            } else {
                RetryDelaysMillis[failures.coerceAtMost(RetryDelaysMillis.lastIndex)].also { failures++ }
            }
            delay(wait)
        }
    }

    private fun restore(channel: UpdateChannel) {
        val kept = Releases.cached(context, channel)
        val staged = ApkDownloader.stagedUpdate(context)
        if (staged == null) {
            offerKept(channel, kept)
            return
        }
        val release = kept.firstOrNull { it.versionName == staged.versionName }
            ?: Release(
                tagName = "v${staged.versionName}",
                versionName = staged.versionName,
                isPreRelease = '-' in staged.versionName,
                notes = null,
                publishedAt = "",
                htmlUrl = "",
                apks = emptyList(),
            )
        UpdateRegistry.setLatest(kept.firstOrNull())
        UpdateRegistry.setAvailable(release)
        UpdateRegistry.update(UpdateState.Downloaded(staged))
    }

    /** What was kept for [channel], shown at once - until GitHub is asked, and whether or not it can be. */
    private fun offerKept(channel: UpdateChannel, kept: List<Release> = Releases.cached(context, channel)) {
        val latest = kept.firstOrNull()
        UpdateRegistry.setLatest(latest)
        if (!UpdateRegistry.holdsDownload) UpdateRegistry.setAvailable(latest?.takeIf { UpdateChecker.isUpdate(context, it) })
    }

    private companion object {
        /** How long the app is off the screen before an update waiting to be installed is. */
        const val LeaveGraceMillis = 10_000L

        /** How often an app left open looks again. */
        const val RecheckIntervalMillis = 30 * 60 * 1000L

        /** How long an unanswered ask waits before the next - growing, so a GitHub that is down is not hammered. */
        val RetryDelaysMillis = longArrayOf(10_000L, 30_000L, 60_000L, 2 * 60_000L, 5 * 60_000L, 15 * 60_000L)
    }
}
