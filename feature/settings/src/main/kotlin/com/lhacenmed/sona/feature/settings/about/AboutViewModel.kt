package com.lhacenmed.sona.feature.settings.about

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.feature.update.github.Contributor
import com.lhacenmed.sona.feature.update.github.Contributors
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

/** Something About loads: on its way, found empty, failed, or [Loaded]. */
sealed interface AboutLoad<out T> {
    data object Loading : AboutLoad<Nothing>
    data object Empty : AboutLoad<Nothing>
    data object Failed : AboutLoad<Nothing>
    data class Loaded<T>(val items: List<T>) : AboutLoad<T>
}

/** A library the app is built with, and its licenses - ArchiveTune's `AboutDependencyLicense`. */
@Immutable
data class DependencyLicense(val name: String, val version: String?, val licenses: String?)

/**
 * About's state - ArchiveTune's `AboutViewModel`: the repository's contributors from the start, and the
 * libraries' licenses once they are asked for.
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _contributors = MutableStateFlow<AboutLoad<Contributor>>(AboutLoad.Loading)
    val contributors: StateFlow<AboutLoad<Contributor>> = _contributors.asStateFlow()

    private val _licenses = MutableStateFlow<AboutLoad<DependencyLicense>?>(null)

    /** The licenses, while their list is open; null while it is closed. */
    val licenses: StateFlow<AboutLoad<DependencyLicense>?> = _licenses.asStateFlow()

    /** Kept once read, so reopening the list shows it at once. */
    private var loadedLicenses: AboutLoad.Loaded<DependencyLicense>? = null

    init {
        loadContributors()
    }

    fun loadContributors() {
        _contributors.value = AboutLoad.Loading
        viewModelScope.launch {
            _contributors.value = Contributors.all(context).fold(
                onSuccess = { if (it.isEmpty()) AboutLoad.Empty else AboutLoad.Loaded(it) },
                onFailure = { AboutLoad.Failed },
            )
        }
    }

    fun openLicenses() {
        loadedLicenses?.let {
            _licenses.value = it
            return
        }
        _licenses.value = AboutLoad.Loading
        viewModelScope.launch {
            _licenses.value = try {
                val licenses = withContext(Dispatchers.IO) { readLicenses() }
                if (licenses.isEmpty()) AboutLoad.Empty else AboutLoad.Loaded(licenses).also { loadedLicenses = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AboutLoad.Failed
            }
        }
    }

    fun closeLicenses() {
        _licenses.value = null
    }

    /** The licenses the build wrote into the app - see the AboutLibraries plugin in `:app`. */
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
