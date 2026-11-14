# Icons

Every icon Sona draws is one of `SonaIcons`, by what it means, never by its look. A screen asks for `SonaIcons.Delete`,
and which glyph draws that meaning is decided in one place: redrawing an icon, or the whole set, never touches a screen.

## How an icon is drawn

| Part | Where | What it holds |
| --- | --- | --- |
| The icons | `core/designsystem/.../icon/SonaIcons.kt` | Each meaning, as an `ImageVector` read from its drawable |
| The drawables | `core/common/src/main/res/drawable/icon_*.xml` | One per icon, generated, never edited by hand |
| The map | `core/common/icons/icons.txt` | Each drawable and the glyph that draws it |
| The glyphs | [Ionicons](https://ionic.io/ionicons), at the version in `gradle/libs.versions.toml`; `core/common/icons/drawn/` | Ionicons' filled icons; the few it lacks, drawn for the app |

The drawables are what everything draws: Compose through `SonaIcons`, and the notifications, the widgets and the floating
window by their resource, `icon_<name>`. Only the icons that exist are in the app - no icon library is bundled.

Outside the set, and drawn as they are: the launcher icon, the splash, the launcher shortcuts, the notification's status
bar icon (`ic_stat_sona`), the update notification's (`ic_update`), the brand marks on About (`ic_sona_mark`, `ic_github`),
and `SonaIcons.CoverPlaceholder` and `SonaIcons.SelectedBadge`, which are drawn from their own paths.

## The map

Each line of `icons.txt` names a drawable and its glyph:

```text
delete                      trash
favorite_border             heart-outline
arrow_back                  arrow-back                    mirrored
repeat_on                   repeat                        on
picture_in_picture          drawn/picture-in-picture
```

- The glyph is an Ionicons name - filled, unless it ends in `-outline` - or `drawn/<name>`, an SVG in
  `core/common/icons/drawn`. Outline is kept for an icon that is the unfilled state of another: `favorite_border`.
- `mirrored` turns the icon round where text runs right to left: anything that points one way.
- `on` adds a dot beneath, for a toggle turned on where it cannot be dimmed when off - the notification.

Then:

```bash
./gradlew generateIcons
```

It reads the Ionicons package (downloaded once, into `build/`), turns each glyph into a vector drawable, one colour,
white, so whatever shows it tints it, and writes them all, removing those the map no longer names. Check in what it
writes.

## Changing an icon

Change its glyph in `icons.txt` and run `generateIcons`. Every place the icon is drawn changes with it.

A glyph Ionicons lacks is drawn as an SVG in `drawn/`, in Ionicons' own terms so it sits among them: a 512 by 512
viewBox, filled shapes, and where a line is drawn, 32 wide with round ends and joins.

## Adding an icon

1. Add a line to `icons.txt`, by what the icon means: `archive    archive`.
2. Run `./gradlew generateIcons`.
3. Add it to `SonaIcons`: `val Archive: ImageVector by icon(R.drawable.icon_archive)`, and use it by that name.

Two meanings that look alike are still two icons, so each can change on its own; one meaning is never two icons. An
icon only the notifications or widgets draw - `repeat_on` - is a drawable alone, used by its resource.

## Changing Ionicons' version

Change `ionicons` in `gradle/libs.versions.toml` and run `generateIcons`. A glyph the new version no longer has fails
the task, naming the icon.
