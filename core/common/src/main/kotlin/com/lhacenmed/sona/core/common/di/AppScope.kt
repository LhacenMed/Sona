package com.lhacenmed.sona.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * A [CoroutineScope] that lives as long as the process does.
 *
 * Library data is process-wide state, not screen-wide state: the same rows feed five tabs, the
 * player, the notification and the theme. Sharing it from a scope that outlives any one ViewModel
 * is what lets the whole app read one already-computed copy instead of each ViewModel querying,
 * mapping and sorting the library again on its own.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        // SupervisorJob so one failing pipeline (say, the folder aggregation) can never cancel the
        // sibling flows that the rest of the app depends on.
        CoroutineScope(SupervisorJob() + dispatcher)
}
