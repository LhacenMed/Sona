package com.lhacenmed.sona.core.navigation

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import java.io.Serializable

/**
 * A destination [HostActivity] can render. Every feature module defines its own `Screen`
 * implementations (a `data object`/`data class` per screen) rather than a single shared sealed
 * class, since `:core:navigation` cannot depend on feature modules.
 *
 * Leave [titleRes] null to have the screen render full-bleed and draw its own top bar; set it to
 * let [HostActivity] show a shared toolbar (with an automatic back button) instead.
 */
interface Screen : Serializable {
    @get:StringRes val titleRes: Int? get() = null
    fun title(context: Context): String? = titleRes?.let(context::getString)

    @Composable
    fun Content()
}
