package com.lhacenmed.sona.feature.settings.appearance

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

private const val AndroidNamespace = "http://schemas.android.com/apk/res/android"

/**
 * The languages the app is in, each named in itself and sorted so: the list the build generates from the translations it
 * holds - see the app's `generateLocaleConfig` - so a translation added is a language offered, with nothing to list by hand.
 */
// The generated list's name is the build's, not a resource of any module's own: it is looked up by name.
@SuppressLint("DiscouragedApi")
internal fun appLanguages(context: Context): List<Locale> {
    val id = context.resources.getIdentifier("_generated_res_locale_config", "xml", context.packageName)
    if (id == 0) return emptyList()
    val parser = context.resources.getXml(id)
    val languages = buildList {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                add(Locale.forLanguageTag(parser.getAttributeValue(AndroidNamespace, "name")))
            }
        }
    }
    parser.close()
    return languages.sortedBy { it.nativeName() }
}

/** The language picked for the app - null while it follows the system's. */
internal fun pickedAppLanguage(): Locale? = AppCompatDelegate.getApplicationLocales()[0]

/** Has the app read in [language] from now on - the system's while null - every screen redrawn in it at once. */
internal fun pickAppLanguage(language: Locale?) {
    AppCompatDelegate.setApplicationLocales(
        language?.let { LocaleListCompat.forLanguageTags(it.toLanguageTag()) } ?: LocaleListCompat.getEmptyLocaleList(),
    )
}

/** [this] language's name in itself: "Français", "العربية". */
internal fun Locale.nativeName(): String = getDisplayName(this).replaceFirstChar { it.titlecase(this) }
