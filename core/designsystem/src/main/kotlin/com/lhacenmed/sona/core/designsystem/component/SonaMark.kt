package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.R

/** The cookie's path is drawn in a box this many units across. */
private const val CookieBoxUnits = 168f

/**
 * The note is laid on the cookie in units of a cookie this many across, as the launch screen lays it - see
 * `ic_splash_anim`: 0.4 of the cookie tall, in its middle, with room around it.
 */
private const val CookieUnits = 288f
private const val NoteScale = 6.095f
private const val NoteLeft = 96.76f
private const val NoteTop = 86.4f

/**
 * Sona's mark: Material's seven-sided cookie with the launcher icon's note on it, in the theme's colours - the primary
 * container and what reads on it - drawn from the very paths the launch screen draws it from, so the two are one mark.
 * Its size is the [modifier]'s.
 *
 * [cookieRotation] turns the cookie alone, in degrees - the note stays upright. [cookieColor] and [noteColor] colour the
 * two, the theme's while null, and [noteAlpha] fades the note; all read as it is drawn, so a mark in flight changes them
 * every frame without recomposing.
 */
@Composable
fun SonaMark(
    modifier: Modifier = Modifier,
    cookieRotation: () -> Float = { 0f },
    cookieColor: (() -> Color)? = null,
    noteColor: (() -> Color)? = null,
    noteAlpha: () -> Float = { 1f },
) {
    val cookieData = stringResource(R.string.sona_mark_cookie_path)
    val noteData = stringResource(R.string.sona_mark_note_path)
    val cookie = remember(cookieData) { PathParser().parsePathString(cookieData).toPath() }
    val note = remember(noteData) { PathParser().parsePathString(noteData).toPath() }
    val restingCookie = MaterialTheme.colorScheme.primaryContainer
    val restingNote = MaterialTheme.colorScheme.onPrimaryContainer
    Spacer(
        modifier = modifier.drawBehind {
            val side = size.minDimension
            rotate(cookieRotation()) {
                scale(side / CookieBoxUnits, pivot = Offset.Zero) {
                    drawPath(cookie, cookieColor?.invoke() ?: restingCookie)
                }
            }
            val unit = side / CookieUnits
            translate(NoteLeft * unit, NoteTop * unit) {
                scale(NoteScale * unit, pivot = Offset.Zero) {
                    drawPath(note, noteColor?.invoke() ?: restingNote, alpha = noteAlpha())
                }
            }
        },
    )
}
