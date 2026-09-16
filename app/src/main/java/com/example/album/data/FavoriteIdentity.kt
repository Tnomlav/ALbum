package com.example.album.data

import org.json.JSONObject

/**
 * A favorite is stored as a media URI, but a URI is not a stable identity: a
 * move, a rename, or a MediaStore re-index changes it and the star silently
 * disappears. [favoriteIdentityKey] is the identity that survives those
 * changes, and [repairFavoriteUris] re-points favorites after a library scan.
 */
data class FavoriteCandidate(val uri: String, val identityKey: String)

fun favoriteIdentityKey(item: MediaItem): String = favoriteIdentityKey(
    isVideo = item.isVideo,
    name = item.name,
    size = item.size,
    dateModified = item.dateModified
)

fun favoriteIdentityKey(isVideo: Boolean, name: String, size: Long, dateModified: Long): String =
    buildString {
        append(if (isVideo) "v" else "p")
        append('|')
        // Names are compared case-insensitively because some providers
        // normalize the given name between scans.
        append(name.lowercase())
        append('|')
        append(size)
        append('|')
        append(dateModified)
    }

fun MediaItem.toFavoriteCandidate(): FavoriteCandidate =
    FavoriteCandidate(uri.toString(), favoriteIdentityKey(this))

data class FavoriteRepairResult(
    val favorites: Set<String>,
    val keys: Map<String, String>,
    /** How many favorites were moved onto a new URI. */
    val reconnected: Int
)

/**
 * Keeps every favorite that still resolves, records an identity key for the
 * ones that do, and re-points the ones whose URI changed when exactly one
 * library item carries the recorded identity. Ambiguous matches are left
 * alone, so a favorite is never silently attached to the wrong file.
 */
fun repairFavoriteUris(
    favorites: Set<String>,
    keys: Map<String, String>,
    items: List<FavoriteCandidate>
): FavoriteRepairResult {
    if (favorites.isEmpty()) return FavoriteRepairResult(favorites, keys, 0)

    val byUri = items.associateBy { it.uri }
    val byKey = items.groupBy { it.identityKey }
    val repaired = LinkedHashSet<String>(favorites.size)
    val updatedKeys = LinkedHashMap(keys)
    var reconnected = 0

    for (favorite in favorites) {
        val direct = byUri[favorite]
        if (direct != null) {
            repaired += favorite
            updatedKeys[favorite] = direct.identityKey
            continue
        }
        val key = keys[favorite]
        val replacement = key?.let { byKey[it] }?.singleOrNull()
        if (key != null && replacement != null) {
            val uri = replacement.uri
            repaired += uri
            updatedKeys.remove(favorite)
            updatedKeys[uri] = key
            reconnected++
        } else {
            // Keep it: the file may only be excluded, hidden, or temporarily
            // unavailable, and dropping it would lose the user's data.
            repaired += favorite
        }
    }
    return FavoriteRepairResult(repaired, updatedKeys, reconnected)
}

fun encodeFavoriteKeys(keys: Map<String, String>): String {
    val json = JSONObject()
    keys.forEach { (uri, key) -> if (uri.isNotBlank() && key.isNotBlank()) json.put(uri, key) }
    return json.toString()
}

fun decodeFavoriteKeys(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { uri ->
                val key = json.optString(uri)
                if (uri.isNotBlank() && key.isNotBlank()) put(uri, key)
            }
        }
    }.getOrDefault(emptyMap())
}
