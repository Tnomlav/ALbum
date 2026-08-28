package com.example.album.ui

import com.example.album.data.MediaAlbum
import com.example.album.data.MediaItem

internal fun searchTextMatches(query: String, vararg values: String): Boolean {
    val text = query.trim()
    return text.isBlank() || values.any { it.contains(text, ignoreCase = true) }
}

internal fun mediaMatchesSearch(query: String, item: MediaItem): Boolean =
    searchTextMatches(query, item.folder, item.name)

internal fun folderMatchesSearch(
    query: String,
    folder: String,
    items: List<MediaItem>,
    fileNames: Set<String> = emptySet()
): Boolean = searchTextMatches(query, folder) ||
    items.any { mediaMatchesSearch(query, it) } ||
    fileNames.any { searchTextMatches(query, it) }

internal fun searchMedia(query: String, media: List<MediaItem>): List<MediaItem> =
    media.filter { mediaMatchesSearch(query, it) }

internal fun searchAlbums(
    media: List<MediaItem>,
    query: String,
    sort: MediaSort,
    sortDirection: SortDirection,
    additionalFolderNames: Set<String> = emptySet(),
    additionalFileNames: Map<String, Set<String>> = emptyMap(),
    matchItems: Boolean = true,
    pinnedFolderName: String? = null
): List<MediaAlbum> {
    fun orderedItems(items: List<MediaItem>): List<MediaItem> {
        val sorted = when (sort) {
            MediaSort.Time, MediaSort.Count -> items.sortedBy { it.dateTaken }
            MediaSort.Name -> items.sortedBy { it.name.lowercase() }
            MediaSort.Size -> items.sortedBy { it.size }
            MediaSort.Duration -> items.sortedBy { it.duration }
        }
        return if (sortDirection == SortDirection.Descending) sorted.reversed() else sorted
    }

    val grouped = media.groupBy { it.folder }
    val albums = (grouped.keys + additionalFolderNames).distinct()
        .map { name ->
            val items = orderedItems(grouped[name].orEmpty())
            MediaAlbum(name, items, items.firstOrNull())
        }
        .filter { album ->
            folderMatchesSearch(
                query,
                album.name,
                if (matchItems) album.items else emptyList(),
                additionalFileNames[album.name].orEmpty()
            )
        }
    val sorted = when (sort) {
        MediaSort.Time -> albums.sortedBy { it.coverItem?.dateTaken ?: Long.MIN_VALUE }
        MediaSort.Name -> albums.sortedBy { it.name.lowercase() }
        MediaSort.Size -> albums.sortedBy { it.items.sumOf(MediaItem::size) }
        MediaSort.Count -> albums.sortedBy { it.items.size }
        MediaSort.Duration -> albums.sortedBy { it.items.sumOf(MediaItem::duration) }
    }
    val ordered = if (sortDirection == SortDirection.Descending) sorted.reversed() else sorted
    val pinnedOrdered = pinnedFolderName?.let { pinned ->
        val pinnedAlbum = ordered.firstOrNull { it.name.equals(pinned, ignoreCase = true) }
            ?: MediaAlbum(pinned, emptyList(), null)
        listOf(pinnedAlbum) + ordered.filterNot { it.name.equals(pinned, ignoreCase = true) }
    } ?: ordered
    return filterAlbums(pinnedOrdered, query, additionalFileNames, pinnedFolderName, matchItems)
}

internal fun filterAlbums(
    albums: List<MediaAlbum>,
    query: String,
    additionalFileNames: Map<String, Set<String>> = emptyMap(),
    pinnedFolderName: String? = null,
    matchItems: Boolean = true
): List<MediaAlbum> {
    if (query.isBlank()) return albums
    return albums.filter { album ->
        album.name.equals(pinnedFolderName, ignoreCase = true) ||
            folderMatchesSearch(
                query,
                album.name,
                if (matchItems) album.items else emptyList(),
                if (matchItems) additionalFileNames[album.name].orEmpty() else emptySet()
            )
    }
}
