package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.model.sort.SortScope
import com.lhacenmed.sona.core.model.sort.SortTarget
import com.lhacenmed.sona.core.model.sort.SortableList
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.sortDataStore by preferencesDataStore(name = "sort_settings")

private const val ORDER_SEPARATOR = ':'
private const val INSTANCE_SEPARATOR = '@'

/**
 * The order each sortable list was last set to.
 *
 * A list there can be many of - one playlist, one album - keeps its order under a key of its own and
 * falls back to the order chosen for its kind. So sorting one playlist leaves every other playlist as
 * it was, while a playlist never sorted on its own still follows the kind's order rather than needing
 * to be given one. That fallback is what makes a new playlist, or a list opened for the first time,
 * already sorted the way its kind is.
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

    /** The order chosen for [target] itself, else for its kind, else null if neither was ever chosen. */
    fun order(target: SortTarget): Setting<SortOrder?> {
        val listKey = target.list.preferenceKey()
        val instanceKey = target.instanceKey()
        return cache.setting { preferences ->
            (instanceKey?.let { preferences[it] } ?: preferences[listKey])?.toSortOrder()
        }
    }

    /**
     * Saves [order] as far as [scope] reaches.
     *
     * Reaching every list of a kind also drops what those lists were each set to on their own, so the
     * order asked for is the order they are all actually shown in - rather than one that the lists with
     * a choice of their own would go on ignoring.
     */
    suspend fun setOrder(target: SortTarget, order: SortOrder, scope: SortScope) {
        val stored = "${order.criterion.name}$ORDER_SEPARATOR${order.direction.name}"
        dataStore.edit { preferences ->
            val instanceKey = target.instanceKey()
            if (scope == SortScope.THIS_LIST && instanceKey != null) {
                preferences[instanceKey] = stored
                return@edit
            }
            preferences[target.list.preferenceKey()] = stored
            preferences.clearInstanceOrders(target.list)
        }
    }
}

private fun SortableList.preferenceKey() = stringPreferencesKey("${name.lowercase()}_sort")

private fun SortTarget.instanceKey() =
    instanceId?.let { stringPreferencesKey("${list.instanceKeyPrefix()}$it") }

private fun SortableList.instanceKeyPrefix() = "${name.lowercase()}_sort$INSTANCE_SEPARATOR"

/** Drops what each list of [list]'s kind was set to on its own, so they all follow the kind's order. */
private fun MutablePreferences.clearInstanceOrders(list: SortableList) {
    val prefix = list.instanceKeyPrefix()
    asMap().keys.filter { it.name.startsWith(prefix) }.forEach { key -> this -= key }
}

private fun String.toSortOrder(): SortOrder? = runCatching {
    SortOrder(
        criterion = SortCriterion.valueOf(substringBefore(ORDER_SEPARATOR)),
        direction = SortDirection.valueOf(substringAfter(ORDER_SEPARATOR)),
    )
}.getOrNull()
