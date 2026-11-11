# Translating Sona

Sona is translated on [Codeberg Translate](https://translate.codeberg.org/projects/sona/), a Weblate server, in the
browser: no account on GitHub, no build, nothing to install. The translations come back to this repository as a pull
request, and the release after it is merged ships them.

## For translators

1. Open [Sona's project](https://translate.codeberg.org/projects/sona/) and sign in with a Codeberg account.
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

Done once, by a maintainer, on Codeberg Translate.

1. The project's main component, `app`, reads this repository's `dev` branch over HTTPS, with no push URL: Weblate has no
   way to write to the repository.
2. The **Component discovery** add-on on it makes a component of every other module with strings - including those
   added later - from one pattern:

   | Field | Value |
   | --- | --- |
   | Regular expression to match translation files | `(?P<component>(core\|feature)/[^/]+)/src/main/res/values/strings\.xml` |
   | File format | Android String Resource |
   | Customize the component name | `{{ component }}` |
   | Define the monolingual base filename | `{{ component }}/src/main/res/values/strings.xml` |
   | Define the base file for new translations | `{{ component }}/src/main/res/values/strings.xml` |
   | Define the file mask | `{{ component }}/src/main/res/values-*/strings.xml` |

3. The [Translations workflow](../.github/workflows/translations.yml) fetches Weblate's read-only copy of the repository
   daily, puts what it has that `dev` does not on the `translations` branch, and opens a pull request into `dev`. Merge it
   with a merge commit, not a squash, so Weblate recognises its commits once it next pulls `dev`. To bring translations in
   sooner, commit them from the project's Repository maintenance page and run the workflow by hand.

## For developers

Write every user-facing text as a string resource in its module's `strings.xml`, never as a literal in Kotlin. A text that
must stay the same in every language - a name, a format, a code - is marked `translatable="false"`. Counts use `<plurals>`,
so every language can say "1 song" and "5 songs" its own way.
