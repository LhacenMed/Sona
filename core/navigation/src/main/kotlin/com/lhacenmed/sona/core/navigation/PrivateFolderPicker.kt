package com.lhacenmed.sona.core.navigation

/**
 * The library's picker of tracks and videos to move into the Private Folder - opened from inside the folder, whose
 * module the library builds on and so cannot be seen from. The library binds the one implementation. It opens on its
 * videos where [startsOnVideos], as the folder's Videos tab asks for it.
 */
interface PrivateFolderPicker {
    fun screen(startsOnVideos: Boolean): Screen
}
