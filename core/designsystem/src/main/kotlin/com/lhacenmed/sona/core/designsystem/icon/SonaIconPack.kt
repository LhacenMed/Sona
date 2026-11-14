package com.lhacenmed.sona.core.designsystem.icon

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import kotlin.reflect.KProperty

/**
 * The app's icon pack: vector drawables that draw its icons, each everywhere it is drawn. A drawable named after an icon - its
 * name in snake case, after `icon_` for [SonaIcons]' and `icon_player_` for [PlayerIcons]' - in any module's resources is drawn
 * in its place: `icon_delete.xml` for [SonaIcons.Delete], `icon_player_play.xml` for [PlayerIcons.Play]. An SVG becomes one
 * through Android Studio's Vector Asset tool. A [SonaIcons] icon the pack has none for is drawn as Material draws it, so a pack
 * can be partial; every [PlayerIcons] icon is one of these drawables. See `docs/ICONS.md`.
 */
object SonaIconPack {
    private var resources: Resources? = null
    private var packageName = ""

    /** Reads the pack from [context]'s resources - as the app starts, before any icon is drawn. */
    fun install(context: Context) {
        resources = context.applicationContext.resources
        packageName = context.packageName
    }

    /** The pack's drawable named [name], or null where it has none. */
    // Looked up by name, once an icon, so that adding a drawable is all a pack takes.
    @SuppressLint("DiscouragedApi")
    internal fun drawable(name: String): ImageVector? {
        val resources = resources ?: return null
        val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
        return ImageVector.vectorResource(theme = null, res = resources, resId = id)
    }
}

/** An icon, as [drawn] unless [SonaIconPack] has a drawable for it: [prefix] and the icon's name in snake case. */
internal class PackIcon(private val prefix: String, private val drawn: () -> ImageVector) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Lazy<ImageVector> {
        val name = prefix + property.name.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").lowercase()
        return lazy { SonaIconPack.drawable(name) ?: drawn() }
    }
}
