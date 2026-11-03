package com.lhacenmed.sona.feature.debug.database

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.database.SonaDatabase
import com.lhacenmed.sona.feature.debug.PAGE_SIZE
import com.lhacenmed.sona.feature.debug.RowPage
import com.lhacenmed.sona.feature.debug.TableRow
import com.lhacenmed.sona.feature.debug.cellText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** How every SQLite database file begins. */
private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()

/** The most rows the SQL console shows of a result. */
private const val MAX_CONSOLE_ROWS = 500

private const val ROW_ID_COLUMN = "_debug_rowid"

internal data class TableInfo(val name: String, val isView: Boolean, val rowCount: Long, val schema: String?)

/** What a statement run in the console came to: the rows it read, or how many it changed. */
internal sealed interface SqlResult {
    data class Rows(val page: RowPage) : SqlResult

    data class Changed(val rowCount: Long) : SqlResult
}

/** A database as this reads and writes it. */
private class Connection(
    val query: (String) -> Cursor,
    val execute: (String, Array<Any?>) -> Unit,
)

/**
 * Every database anywhere in the app's private folder, told by how its file begins rather than by where it lies - the
 * library's, the Private Folder's in a folder of its own, and any added later - read and changed by SQL. Named by path.
 *
 * The library's goes through Room's own connection, in Room's own transaction: Room hears of a change only by triggers
 * it keeps on that connection, and through it every list on screen follows the change at once. Any other is opened
 * for the moment it is used, and what shows it reads the change the next time it reads the database.
 */
@Singleton
internal class DatabaseInspector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sonaDatabase: SonaDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val sonaDatabasePath = context.getDatabasePath(SonaDatabase.FILE_NAME).path

    suspend fun files(): List<String> = withContext(ioDispatcher) {
        File(context.applicationInfo.dataDir).walkTopDown().filter(::isDatabase).map(File::getPath).sorted().toList()
    }

    suspend fun tables(file: String): List<TableInfo> = withConnection(file) { connection ->
        connection.query("SELECT name, type, sql FROM sqlite_master WHERE type IN ('table', 'view') AND name NOT LIKE 'sqlite_%' ORDER BY name").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val rowCount = connection.query("SELECT COUNT(*) FROM ${quoted(name)}").use { it.moveToFirst(); it.getLong(0) }
                    add(TableInfo(name, cursor.getString(1) == "view", rowCount, cursor.getString(2)))
                }
            }
        }
    }

    /** A page of [table], narrowed by [filter] - what goes after `WHERE` - from [offset] on. A view's rows have no id to change them by. */
    suspend fun page(file: String, table: String, isView: Boolean, filter: String, offset: Int): RowPage = withConnection(file) { connection ->
        val rowId = if (isView) "" else "rowid AS $ROW_ID_COLUMN, "
        val where = if (filter.isBlank()) "" else "WHERE $filter"
        connection.query("SELECT $rowId* FROM ${quoted(table)} $where LIMIT ${PAGE_SIZE + 1} OFFSET $offset").use { it.toPage(PAGE_SIZE) }
    }

    /** Puts [value] - or NULL - in [column] of the row [rowId] of [table]. */
    suspend fun setCell(file: String, table: String, rowId: Long, column: String, value: String?) = write(file) { connection ->
        connection.execute("UPDATE ${quoted(table)} SET ${quoted(column)} = ? WHERE rowid = ?", arrayOf(value, rowId))
    }

    suspend fun deleteRows(file: String, table: String, rowIds: Collection<Long>) = write(file) { connection ->
        connection.execute("DELETE FROM ${quoted(table)} WHERE rowid IN (${rowIds.joinToString()})", emptyArray())
    }

    /** Runs [sql]: what reads gives its rows, and anything else what it changed. A failure is thrown, for the console to show. */
    suspend fun run(file: String, sql: String): SqlResult {
        val isRead = sql.trimStart().substringBefore(' ').uppercase() in setOf("SELECT", "PRAGMA", "EXPLAIN", "WITH", "VALUES")
        if (isRead) return withConnection(file) { connection -> SqlResult.Rows(connection.query(sql).use { it.toPage(MAX_CONSOLE_ROWS) }) }
        return write(file) { connection ->
            connection.execute(sql, emptyArray())
            SqlResult.Changed(connection.query("SELECT changes()").use { it.moveToFirst(); it.getLong(0) })
        }
    }

    private suspend fun <T> write(file: String, block: (Connection) -> T): T = withConnection(file) { connection ->
        if (file == sonaDatabasePath) sonaDatabase.runInTransaction<T> { block(connection) } else block(connection)
    }

    private suspend fun <T> withConnection(file: String, block: (Connection) -> T): T = withContext(ioDispatcher) {
        if (file == sonaDatabasePath) {
            val database = sonaDatabase.openHelper.writableDatabase
            block(Connection(query = { sql -> database.query(sql) }, execute = { sql, args -> database.execSQL(sql, args) }))
        } else {
            SQLiteDatabase.openDatabase(file, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                block(Connection(query = { sql -> database.rawQuery(sql, null) }, execute = { sql, args -> database.execSQL(sql, args) }))
            }
        }
    }
}

private fun isDatabase(file: File): Boolean {
    if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
    val header = ByteArray(SQLITE_HEADER.size)
    return runCatching { file.inputStream().use { it.read(header) } }.isSuccess && header.contentEquals(SQLITE_HEADER)
}

private fun quoted(identifier: String) = "\"" + identifier.replace("\"", "\"\"") + "\""

/** Up to [limit] rows of this cursor, the column naming each row's id taken out of them. */
private fun Cursor.toPage(limit: Int): RowPage {
    val rowIdColumn = getColumnIndex(ROW_ID_COLUMN)
    val columns = columnNames.indices.filter { it != rowIdColumn }
    val rows = buildList {
        while (size < limit && moveToNext()) {
            add(TableRow(rowIdColumn.takeIf { it >= 0 }?.let(::getLong), columns.map { cellText(it) }))
        }
    }
    return RowPage(columns.map { columnNames[it] }, rows, hasMore = moveToNext())
}
