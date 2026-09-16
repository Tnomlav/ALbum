package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteIdentityTest {
    private fun candidate(uri: String, name: String, size: Long = 100L, modified: Long = 5L, video: Boolean = false) =
        FavoriteCandidate(uri, favoriteIdentityKey(video, name, size, modified))

    @Test
    fun aFavoriteThatStillResolvesKeepsItsUriAndRecordsItsIdentity() {
        val uri = "content://media/external/images/media/1"
        val result = repairFavoriteUris(
            favorites = setOf(uri),
            keys = emptyMap(),
            items = listOf(candidate(uri, "cat.jpg"))
        )

        assertEquals(setOf(uri), result.favorites)
        assertEquals(favoriteIdentityKey(false, "cat.jpg", 100L, 5L), result.keys[uri])
        assertEquals(0, result.reconnected)
    }

    @Test
    fun aMovedFileIsReconnectedThroughItsIdentity() {
        val oldUri = "content://media/external/images/media/1"
        val newUri = "content://media/external/images/media/99"
        val key = favoriteIdentityKey(false, "cat.jpg", 100L, 5L)

        val result = repairFavoriteUris(
            favorites = setOf(oldUri),
            keys = mapOf(oldUri to key),
            items = listOf(candidate(newUri, "cat.jpg"))
        )

        assertEquals(setOf(newUri), result.favorites)
        assertEquals(key, result.keys[newUri])
        assertTrue(oldUri !in result.keys)
        assertEquals(1, result.reconnected)
    }

    @Test
    fun identicalCopiesAreLeftAloneInsteadOfGuessing() {
        val oldUri = "content://media/external/images/media/1"
        val key = favoriteIdentityKey(false, "cat.jpg", 100L, 5L)

        val result = repairFavoriteUris(
            favorites = setOf(oldUri),
            keys = mapOf(oldUri to key),
            items = listOf(
                candidate("content://media/external/images/media/2", "cat.jpg"),
                candidate("content://media/external/images/media/3", "cat.jpg")
            )
        )

        assertEquals(setOf(oldUri), result.favorites)
        assertEquals(0, result.reconnected)
    }

    @Test
    fun aFavoriteOutsideTheCurrentLibraryIsNeverDropped() {
        val uri = "content://media/external/images/media/1"

        val result = repairFavoriteUris(
            favorites = setOf(uri),
            keys = mapOf(uri to favoriteIdentityKey(false, "holiday.jpg", 1L, 1L)),
            items = listOf(candidate("content://media/external/images/media/7", "other.jpg"))
        )

        assertEquals(setOf(uri), result.favorites)
        assertEquals(0, result.reconnected)
    }

    @Test
    fun anEmptyLibraryChangesNothing() {
        val uri = "content://media/external/images/media/1"

        val result = repairFavoriteUris(setOf(uri), mapOf(uri to "p|x"), emptyList())

        assertEquals(setOf(uri), result.favorites)
        assertEquals(mapOf(uri to "p|x"), result.keys)
    }

    @Test
    fun favoritesWithoutAnyIdentityAlsoChangeNothing() {
        val result = repairFavoriteUris(emptySet(), emptyMap(), listOf(candidate("content://x/1", "a.jpg")))

        assertTrue(result.favorites.isEmpty())
        assertTrue(result.keys.isEmpty())
    }

    @Test
    fun identitySurvivesOnlyWhitespaceFreeFieldsItActuallyHas() {
        val sameNameDifferentSize = favoriteIdentityKey(false, "cat.jpg", 100L, 5L) !=
            favoriteIdentityKey(false, "cat.jpg", 101L, 5L)
        val caseInsensitiveName = favoriteIdentityKey(false, "CAT.JPG", 100L, 5L) ==
            favoriteIdentityKey(false, "cat.jpg", 100L, 5L)

        assertTrue(sameNameDifferentSize)
        assertTrue(caseInsensitiveName)
    }

    @Test
    fun identityKeysSurviveAJsonRoundTrip() {
        val keys = mapOf(
            "content://media/external/images/media/1" to "p|cat.jpg|100|5",
            "content://media/external/video/media/2" to "v|clip.mp4|2048|9"
        )

        assertEquals(keys, decodeFavoriteKeys(encodeFavoriteKeys(keys)))
        assertTrue(decodeFavoriteKeys(null).isEmpty())
        assertTrue(decodeFavoriteKeys("not json").isEmpty())
    }
}
