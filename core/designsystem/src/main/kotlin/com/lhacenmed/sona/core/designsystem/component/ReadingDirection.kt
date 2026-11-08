package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.ui.unit.LayoutDirection

/**
 * The direction [text] reads in, by its first letter that has one - null for text with none: numbers, "...". A name in
 * Arabic reads right to left in an app laid out left to right, and stands and moves as it reads.
 */
fun readingDirectionOf(text: String): LayoutDirection? {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
            -> return LayoutDirection.Rtl

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            -> return LayoutDirection.Ltr
        }
    }
    return null
}
