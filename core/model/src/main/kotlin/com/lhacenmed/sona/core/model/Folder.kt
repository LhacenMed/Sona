package com.lhacenmed.sona.core.model

/** A runtime aggregation over [Track.folderPath] - not a persisted entity. */
data class Folder(
    val path: String,
    val name: String,
    val trackCount: Int,
)
