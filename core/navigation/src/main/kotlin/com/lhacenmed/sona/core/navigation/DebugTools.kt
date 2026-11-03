package com.lhacenmed.sona.core.navigation

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The way into the debug tools, whose module only a debug build has. Asked for as an `Optional`, so what opens it -
 * the settings - knows nothing of it, and in any other build simply finds nothing there.
 */
interface DebugTools {
    val screen: Screen
    val summary: String
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugToolsSlotModule {
    @BindsOptionalOf
    abstract fun optionalDebugTools(): DebugTools
}
