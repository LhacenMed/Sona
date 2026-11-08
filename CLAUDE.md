# Sona - working on this project

Read before writing any UI. Most of this is what the code already does; it is here so it is known before the first
line, rather than found out afterwards.

## UI: the design system comes first

The app uses Material 3 Expressive. Before writing a control, look in `core/designsystem/.../component` and
`core/designsystem/.../theme/SonaComponentStyle.kt` for the app's own.

| Need | Use | Not |
|---|---|---|
| Labelled button | Material's, with `shapes = buttonPressShapes()` | a button without `shapes` |
| Icon button | `SonaIconButton`, or Material's with `shapes = iconButtonPressShapes()` | `IconButton` without `shapes` |
| Several icon buttons | `SonaIconButtonGroup` | a `Row` of them |
| Buttons at the foot of a dialog or sheet | `actionButton` in `SonaActionButtonGroup` | `TextButton` in a `Row` |
| Dialog | `SonaDialog`; `SonaConfirmationDialog` to confirm an operation | `AlertDialog` |
| Selecting rows | `SelectionState` with `selectAllAction` and `toTopBarSelection`, `SonaListRow` or `Modifier.selectableRow`, `rememberDragSelection` | a checkbox or a delete button per row |
| Where a browser is | `SonaPathBar` | a title showing the path |
| A list of rows | `SonaListRow`, in `section` where they are grouped | a hand-made `Row` |
| A settings-style row | `feature/settings/.../component/SettingsItems.kt` | a hand-made `ListItem` |
| A list's end clear of the mini player | `LocalBottomContentPadding` | a fixed padding |
| A screen's scrolling list or column | `FastScroller` around it, with `sectionAt` where it is sorted | a bare `LazyColumn` or `verticalScroll` |

`./gradlew checkExpressiveComponents` enforces the button and dialog rows, and every build runs it. Fix what it
reports by using the component above.

## How actions behave

- A dialog that offers an action also offers the way out, first: `Cancel`, then the action. An error or a notice has `OK` alone.
- Deleting is done on a selection, from the top bar, and confirmed with `SonaConfirmationDialog` - never a delete button in a dialog that does something else.
- A row's tap opens it; a long press selects it, and a drag selects across its neighbours. Tapping while selecting toggles.
- A browser moves within one screen, with back going up a level, rather than opening a screen per folder.

## Data

The app is live, so a schema change needs an explicit additive migration: the destructive fallback would erase users' data.

## Screens and modules

- A screen is a `Screen` (a `data object` or `data class`) opened with `LocalNavigator.current.go(...)`.
- `:feature:debug` exists only in debug builds. Nothing outside it may depend on it; it is reached through
  `core/navigation/.../DebugTools.kt`.
