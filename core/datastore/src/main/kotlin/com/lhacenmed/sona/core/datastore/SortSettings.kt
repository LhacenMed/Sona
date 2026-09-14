package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortableList
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.sortDataStore by preferencesDataStore(name = "sort_settings")

private const val ORDER_SEPARATOR = ':'

/**
 * The order each sortable list was last set to.
 *
 * Stores only what the user chose. Whether a stored order is one its list still offers - and what the
 * list falls back to when it is not, or was never set - is decided where orders are applied, next to
 * the criteria themselves.
 */
@Singleton
class SortSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.sortDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /** The order chosen for [list], or null if none was ever chosen or it no longer names a known one. */
    fun order(list: SortableList): Setting<SortOrder?> {
        val key = list.preferenceKey()
        return cache.setting { preferences -> preferences[key]?.toSortOrder() }
    }

    suspend fun setOrder(list: SortableList, order: SortOrder) {
        dataStore.edit {
            it[list.preferenceKey()] = "${order.criterion.name}$ORDER_SEPARATOR${order.direction.name}"
        }
    }
}

private fun SortableList.preferenceKey() = stringPreferencesKey("${name.lowercase()}_sort")

private fun String.toSortOrder(): SortOrder? = runCatching {
    SortOrder(
        criterion = SortCriterion.valueOf(substringBefore(ORDER_SEPARATOR)),
        direction = SortDirection.valueOf(substringAfter(ORDER_SEPARATOR)),
    )
}.getOrNull()
