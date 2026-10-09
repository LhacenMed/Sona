package com.lhacenmed.sona.core.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.model.AppFont

/** ArchiveTune's own typeface - what [AppFont.POPPINS] sets text in. */
private val PoppinsFontFamily = FontFamily(Font(R.font.poppins))

/**
 * Auxio's typeface. Auxio sets its titles and labels in the semibold cut, so the weights Material gives
 * those - medium and up - are drawn from it too.
 */
private val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Material's type scale, as it is, for [AppFont.SYSTEM]. */
private val SystemTypography = Typography()

/** The type scale [font] sets the app's text in. A custom font that cannot be read gives way to the default font. */
@Composable
internal fun rememberTypography(font: AppFont, customFontFamily: FontFamily?): Typography =
    remember(font, customFontFamily) {
        when (font) {
            AppFont.POPPINS -> typographyFor(PoppinsFontFamily)
            AppFont.SYSTEM -> SystemTypography
            AppFont.INTER -> typographyFor(InterFontFamily)
            AppFont.CUSTOM -> typographyFor(customFontFamily ?: PoppinsFontFamily)
        }
    }

/** Material's type scale, every style - the expressive emphasized ones too - set in [fontFamily]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun typographyFor(fontFamily: FontFamily): Typography {
    fun TextStyle.inFamily() = copy(fontFamily = fontFamily)
    return with(SystemTypography) {
        copy(
            displayLarge = displayLarge.inFamily(),
            displayMedium = displayMedium.inFamily(),
            displaySmall = displaySmall.inFamily(),
            headlineLarge = headlineLarge.inFamily(),
            headlineMedium = headlineMedium.inFamily(),
            headlineSmall = headlineSmall.inFamily(),
            titleLarge = titleLarge.inFamily(),
            titleMedium = titleMedium.inFamily(),
            titleSmall = titleSmall.inFamily(),
            bodyLarge = bodyLarge.inFamily(),
            bodyMedium = bodyMedium.inFamily(),
            bodySmall = bodySmall.inFamily(),
            labelLarge = labelLarge.inFamily(),
            labelMedium = labelMedium.inFamily(),
            labelSmall = labelSmall.inFamily(),
            displayLargeEmphasized = displayLargeEmphasized.inFamily(),
            displayMediumEmphasized = displayMediumEmphasized.inFamily(),
            displaySmallEmphasized = displaySmallEmphasized.inFamily(),
            headlineLargeEmphasized = headlineLargeEmphasized.inFamily(),
            headlineMediumEmphasized = headlineMediumEmphasized.inFamily(),
            headlineSmallEmphasized = headlineSmallEmphasized.inFamily(),
            titleLargeEmphasized = titleLargeEmphasized.inFamily(),
            titleMediumEmphasized = titleMediumEmphasized.inFamily(),
            titleSmallEmphasized = titleSmallEmphasized.inFamily(),
            bodyLargeEmphasized = bodyLargeEmphasized.inFamily(),
            bodyMediumEmphasized = bodyMediumEmphasized.inFamily(),
            bodySmallEmphasized = bodySmallEmphasized.inFamily(),
            labelLargeEmphasized = labelLargeEmphasized.inFamily(),
            labelMediumEmphasized = labelMediumEmphasized.inFamily(),
            labelSmallEmphasized = labelSmallEmphasized.inFamily(),
        )
    }
}
