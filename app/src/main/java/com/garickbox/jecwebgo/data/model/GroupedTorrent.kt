package com.garickbox.jecwebgo.data.model

import com.garickbox.jecwebgo.data.network.TorrentItem

data class GroupedTorrent(
    val cleanTitle: String,
    val year: String,
    val poster: String,
    val rating: Double,
    val genre: String,
    val description: String,
    val director: String,
    val actors: String,
    val items: List<TorrentItem>
) {
    val count: Int get() = items.size
    val maxSeeds: Int get() = items.maxOfOrNull { it.seeds } ?: 0
}
