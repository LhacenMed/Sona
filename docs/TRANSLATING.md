# Translating Sona

Sona is translated on [Weblate](https://hosted.weblate.org), in the browser: no account on GitHub, no build, nothing to
install. Weblate commits each translation back to this repository, and the next release ships it.

## For translators

1. Open Sona's project on Weblate and sign in.
2. Pick your language, or start it if it isn't there yet.
3. Translate the strings of any component: each is one part of the app, such as Settings or Downloads.

Keep every placeholder - `%1$s`, `%d`, `%%` - exactly as it is, wherever your language needs it in the sentence. A string
marked read-only is a name, a code or a format that stays the same in every language.

A language appears in the app's language picker, and in Android's own per-app language settings, as soon as one of its
translations is in a release.

## How it fits together

- **Strings**: every text the app shows is a string resource, in its module's `src/main/res/values/strings.xml`, in English.
  A translation is the same file in `values-<language>`: `values-fr`, `values-pt-rBR`, `values-zh-rTW`.
- **Languages**: the build ships English and every language a module has a translation for, and generates the list Android
  and the language picker show from them. A new language needs no change outside its translated files.
- **Picking one**: Settings › Appearance › App language, or Android's per-app language settings from Android 13. The app
  follows the system's language until one is picked.

## Setting up the Weblate project

Done once, by a maintainer, on [Hosted Weblate](https://hosted.weblate.org) - free for open-source projects.

1. Create the project, pointed at this repository's `dev` branch, with push access for Weblate's bot so it opens pull
   requests with the translations.
2. Add the **Component discovery** add-on, which makes a component of every module with strings - including those added
   later - from this one pattern:

   | Field | Value |
   | --- | --- |
   | Regular expression to match translation files | `(?P<component>[^/]+(/[^/]+)?)/src/main/res/values-(?P<language>[^/]+)/strings\.xml` |
   | File format | Android String Resource |
   | Customize the component name | `{{ component }}` |
   | Define the monolingual base filename | `{{ component }}/src/main/res/values/strings.xml` |
   | Define the base file for new translations | `{{ component }}/src/main/res/values/strings.xml` |

3. Add a link to the project in the README, with its translation status badge.

## For developers

Write every user-facing text as a string resource in its module's `strings.xml`, never as a literal in Kotlin. A text that
must stay the same in every language - a name, a format, a code - is marked `translatable="false"`. Counts use `<plurals>`,
so every language can say "1 song" and "5 songs" its own way.
