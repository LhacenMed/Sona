package com.lhacenmed.sona

import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.datastore.CoverMode
import com.lhacenmed.sona.core.datastore.ImageSettings
import com.lhacenmed.sona.core.datastore.ThemeSettings
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.CoverStyle
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The stored cover settings, as the [CoverStyle] every cover in the app is drawn with.
 *
 * Starts from the stored values, already in memory, so the first cover drawn is already the one the
 * user chose rather than a default replaced a moment later.
 */
@Singleton
class SonaCoverStyle @Inject constructor(
    @ApplicationScope scope: CoroutineScope,
    imageSettings: ImageSettings,
    themeSettings: ThemeSettings,
) : AppCoverStyle {

    override val style: StateFlow<CoverStyle> = combine(
        imageSettings.coverMode.flow,
        imageSettings.forceSquareCovers.flow,
        themeSettings.roundMode.flow,
    ) { coverMode, forceSquareCovers, roundMode ->
        coverStyleOf(coverMode, forceSquareCovers, roundMode)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        coverStyleOf(
            imageSettings.coverMode.value,
            imageSettings.forceSquareCovers.value,
            themeSettings.roundMode.value,
        ),
    )
}

/** Each quality level decodes covers no larger than the size Auxio stores them at under it. */
private fun coverStyleOf(coverMode: CoverMode, forceSquareCovers: Boolean, roundMode: Boolean) = CoverStyle(
    showsCovers = coverMode != CoverMode.OFF,
    maxResolutionPx = when (coverMode) {
        CoverMode.OFF, CoverMode.AS_IS -> null
        CoverMode.SAVE_SPACE -> 500
        CoverMode.BALANCED -> 750
        CoverMode.HIGH_QUALITY -> 1000
    },
    isForcedSquare = forceSquareCovers,
    isRounded = roundMode,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class CoverStyleModule {
    @Binds
    abstract fun bindAppCoverStyle(style: SonaCoverStyle): AppCoverStyle
}
