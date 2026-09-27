package com.lhacenmed.sona.feature.settings.about

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.feature.settings.component.SettingsLoad
import com.lhacenmed.sona.feature.settings.component.settingsLoadOf
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A library the app is built with, and its licenses - ArchiveTune's `AboutDependencyLicense`. */
@Immutable
data class DependencyLicense(val name: String, val version: String?, val licenses: String?)

/** The libraries' licenses, as the build wrote them into the app - see the AboutLibraries plugin in `:app`. */
@HiltViewModel
class LicensesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _licenses = MutableStateFlow<SettingsLoad<DependencyLicense>>(SettingsLoad.Loading)
    val licenses: StateFlow<SettingsLoad<DependencyLicense>> = _licenses.asStateFlow()

    init {
        load()
    }

    fun load() {
        _licenses.value = SettingsLoad.Loading
        viewModelScope.launch {
            _licenses.value = try {
                settingsLoadOf(withContext(Dispatchers.IO) { readLicenses() })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SettingsLoad.Failed
            }
        }
    }

    private fun readLicenses(): List<DependencyLicense> =
        Libs.Builder().withContext(context).build().libraries
            .map { library ->
                DependencyLicense(
                    name = library.name.ifBlank { library.uniqueId },
                    version = library.artifactVersion?.takeIf { it.isNotBlank() },
                    licenses = library.licenses.map { it.name }.filter { it.isNotBlank() }.distinct()
                        .joinToString().takeIf { it.isNotBlank() },
                )
            }
            .filter { it.name.isNotBlank() }
}
