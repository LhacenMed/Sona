package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val STEP = stringPreferencesKey("step")
private val PATH = stringPreferencesKey("path")
private val FINISHED = booleanPreferencesKey("finished")

/** One screen of the first-launch setup, in the order the companion turns through them - see the setup's companion. */
enum class OnboardingStep { WELCOME, ACCESS, RESTORE, FOLDERS, LIBRARY, DOWNLOADS, LOOK, UPDATES, DONE }

/**
 * The way through the setup - the one place its steps are put in order. Restoring asks for access first: the restore
 * looks for the backup's tracks on this device, which it can only read once access is granted.
 */
enum class OnboardingPath(val steps: List<OnboardingStep>) {
    FRESH(
        listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.ACCESS,
            OnboardingStep.FOLDERS,
            OnboardingStep.LIBRARY,
            OnboardingStep.DOWNLOADS,
            OnboardingStep.LOOK,
            OnboardingStep.UPDATES,
            OnboardingStep.DONE,
        ),
    ),
    RESTORE(listOf(OnboardingStep.WELCOME, OnboardingStep.ACCESS, OnboardingStep.RESTORE, OnboardingStep.DONE)),
}

/** Where the setup stands: on [step] of [path]. */
data class OnboardingProgress(val path: OnboardingPath, val step: OnboardingStep)

/**
 * The first-launch setup's own bookkeeping - which step it is on, so leaving halfway resumes there, and whether it is
 * done. Kept out of backups: a restore brings back what was chosen, never whether this device has been set up.
 */
@Singleton
class OnboardingSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val dataStore = context.onboardingDataStore
    private val cache = PreferencesCache(dataStore, scope)

    /**
     * Whether Sona was used before the setup existed: its library is there but this file never was. Someone who already
     * uses the app has set it up in Settings, so the setup is done for them. Read from what is stored, not from install
     * times - clearing the app's data, or reinstalling over it, leaves those as they were. Decided once, as the file is
     * first written, so the library this very setup goes on to read is not taken for an old one at the next launch.
     */
    private val wasUsedBefore = !context.preferencesDataStoreFile("onboarding_state").exists() &&
        context.getDatabasePath("sona.db").exists()

    init {
        scope.launch { dataStore.edit { if (FINISHED !in it) it[FINISHED] = wasUsedBefore } }
    }

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /** Where the setup stands - or null once it is done. */
    val progress: Setting<OnboardingProgress?> = cache.setting { preferences ->
        if (preferences[FINISHED] ?: wasUsedBefore) return@setting null
        val path = preferences.enum(PATH, OnboardingPath.FRESH)
        val step = preferences.enum(STEP, OnboardingStep.WELCOME).takeIf { it in path.steps } ?: OnboardingStep.WELCOME
        OnboardingProgress(path, step)
    }

    suspend fun setProgress(progress: OnboardingProgress) {
        dataStore.edit {
            it[PATH] = progress.path.name
            it[STEP] = progress.step.name
        }
    }

    /** Has the setup start over from its first step at the next launch - returning once [progress] says so. */
    suspend fun restart() {
        dataStore.edit {
            it.clear()
            it[FINISHED] = false
        }
        progress.flow.first { it != null }
    }

    suspend fun finish() {
        dataStore.edit {
            it.clear()
            it[FINISHED] = true
        }
    }
}
