package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lhacenmed.sona.core.database.entity.ArrangementEntity
import com.lhacenmed.sona.core.model.sort.SortableList
import kotlinx.coroutines.flow.Flow

/** A track's place in a list's hand-made order. */
data class ArrangedTrack(
    val trackId: Long,
    val position: Int,
)

@Dao
interface ArrangementDao {

    /** The hand-made order of one list, empty until it is first arranged. */
    @Query("SELECT trackId, position FROM arrangements WHERE list = :list AND instanceId = :instanceId")
    fun observe(list: SortableList, instanceId: String): Flow<List<ArrangedTrack>>

    @Query("DELETE FROM arrangements WHERE list = :list AND instanceId = :instanceId")
    suspend fun delete(list: SortableList, instanceId: String)

    @Insert
    suspend fun insert(rows: List<ArrangementEntity>)

    /**
     * Makes [trackIds] the list's hand-made order, as one change. Every track the list shows is
     * written, so a track it gains afterwards is the only kind without a row.
     */
    @Transaction
    suspend fun replace(list: SortableList, instanceId: String, trackIds: List<Long>) {
        delete(list, instanceId)
        insert(trackIds.distinct().mapIndexed { position, trackId -> ArrangementEntity(list, instanceId, trackId, position) })
    }

    @Query("DELETE FROM arrangements")
    suspend fun deleteAll()
}
