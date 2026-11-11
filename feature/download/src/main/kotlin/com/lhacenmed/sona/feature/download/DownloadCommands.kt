package com.lhacenmed.sona.feature.download

import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadCommandEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.downloader.ReadingArguments
import com.lhacenmed.sona.core.downloader.splitArguments
import com.lhacenmed.sona.core.model.DownloadCommand
import com.lhacenmed.sona.core.model.DownloadKind
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The saved commands, as they are now. */
internal suspend fun DownloadDao.savedCommands(): List<DownloadCommand> = commands().map(DownloadCommandEntity::toDomain)

/**
 * The arguments a download of [url] as [kind] is given: those of every saved command added to its kind and for its link,
 * then [picked] - the one picked for it - each once. Null for none.
 */
internal suspend fun DownloadDao.commandArgumentsFor(kind: DownloadKind, url: String, picked: String?): String? =
    (savedCommands().filter { it.addsTo(kind, url) }.map { it.arguments } + listOfNotNull(picked)).distinct().joinToString(" ").ifBlank { null }

@Module
@InstallIn(SingletonComponent::class)
internal object DownloadCommandsModule {

    /** What a read of a link with yt-dlp is given beside its own: every saved command added to reading that is for it. */
    @Provides
    fun readingArguments(dao: DownloadDao): ReadingArguments = ReadingArguments { url ->
        dao.savedCommands().filter { it.addsToReading && it.isFor(url) }.flatMap { splitArguments(it.arguments) }
    }
}
