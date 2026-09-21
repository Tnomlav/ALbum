package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Pixiv library walk used to re-read every folder on every visit: one
 * provider query per folder plus four per file, which is what made a large
 * archive take seconds. It now lists each folder once, fingerprints the
 * listing, and replays unchanged folders straight from the index.
 */
class PixivWalkIndexTest {

    private fun file(uri: String, name: String, size: Long = 10L, modified: Long = 1L) =
        PixivDirEntry(uri, name, isDirectory = false, size = size, lastModified = modified, mimeType = "image/jpeg")

    private fun folder(uri: String, name: String, modified: Long = 1L) =
        PixivDirEntry(
            uri,
            name,
            isDirectory = true,
            size = 0L,
            lastModified = modified,
            mimeType = "vnd.android.document/directory"
        )

    @Test
    fun theFingerprintIgnoresOrderButNotChanges() {
        val listing = listOf(file("u1", "a.jpg"), file("u2", "b.jpg"))
        assertEquals(
            pixivDirectoryFingerprint(listing),
            pixivDirectoryFingerprint(listOf(file("u2", "b.jpg"), file("u1", "a.jpg")))
        )
        assertTrue(
            pixivDirectoryFingerprint(listing) !=
                pixivDirectoryFingerprint(listOf(file("u1", "a.jpg"), file("u2", "renamed.jpg")))
        )
        assertTrue(
            pixivDirectoryFingerprint(listing) !=
                pixivDirectoryFingerprint(listOf(file("u1", "a.jpg"), file("u2", "b.jpg", size = 99L)))
        )
        assertTrue(
            pixivDirectoryFingerprint(listing) !=
                pixivDirectoryFingerprint(listOf(file("u1", "a.jpg"), file("u2", "b.jpg", modified = 7L)))
        )
        assertTrue(
            pixivDirectoryFingerprint(listing) !=
                pixivDirectoryFingerprint(listOf(file("u1", "a.jpg")))
        )
    }

    @Test
    fun onlyAMatchingListingIsReusable() {
        val index = PixivWalkIndex()
        val entries = listOf(file("u1", "a.jpg"))
        index.put("dir", entries)
        assertEquals(entries, index.reusable("dir", entries))
        assertNull(index.reusable("dir", listOf(file("u1", "a.jpg", size = 5L))))
        assertNull(index.reusable("other", entries))
    }

    @Test
    fun theSecondWalkReplaysUnchangedSubtrees() {
        val listings = mapOf(
            "root" to listOf(folder("artist", "artist")),
            "artist" to listOf(file("img", "1.jpg"))
        )
        val queries = mutableListOf<String>()
        val list: (String) -> List<PixivDirEntry>? = { uri ->
            queries += uri
            listings[uri]
        }

        val first = walkPixivFolders(
            rootUri = "root",
            folderName = "Pixiv",
            previous = PixivWalkIndex(),
            list = list,
            includeHidden = false,
            onItem = { _, _, _ -> }
        )
        assertEquals(listOf("root", "artist"), queries)
        assertEquals(2, first.second.directoriesListed)
        assertEquals(0, first.second.directoriesReused)
        assertEquals(1, first.second.itemsFound)

        queries.clear()
        val second = walkPixivFolders(
            rootUri = "root",
            folderName = "Pixiv",
            previous = first.first,
            list = list,
            includeHidden = false,
            onItem = { _, _, _ -> }
        )
        // Only the root is listed again; the unchanged artist folder and its
        // contents come from the index.
        assertEquals(listOf("root"), queries)
        assertEquals(1, second.second.directoriesListed)
        assertEquals(1, second.second.directoriesReused)
        assertEquals(1, second.second.itemsFound)
    }

    @Test
    fun aChangedFolderIsListedAgain() {
        val listings = mutableMapOf(
            "root" to listOf(folder("artist", "artist")),
            "artist" to listOf(file("img", "1.jpg"))
        )
        val queries = mutableListOf<String>()
        val list: (String) -> List<PixivDirEntry>? = { uri ->
            queries += uri
            listings[uri]
        }
        val first = walkPixivFolders("root", "Pixiv", PixivWalkIndex(), list, false, { _, _, _ -> })

        // A new file inside the artist folder changes both the folder listing and
        // the artist's own entry in the root listing (its timestamp), so nothing
        // stale is served.
        listings["artist"] = listOf(file("img", "1.jpg"), file("img2", "2.jpg"))
        listings["root"] = listOf(folder("artist", "artist", modified = 2L))
        queries.clear()
        val second = walkPixivFolders("root", "Pixiv", first.first, list, false, { _, _, _ -> })
        assertEquals(listOf("root", "artist"), queries)
        assertEquals(2, second.second.directoriesListed)
        assertEquals(0, second.second.directoriesReused)
        assertEquals(2, second.second.itemsFound)
    }

    @Test
    fun hiddenAndTrashedEntriesAreSkipped() {
        val listings = mapOf(
            "root" to listOf(
                file("hidden", ".hidden.jpg"),
                file("trashed", ".trashed-123.jpg"),
                folder("hiddenDir", ".private"),
                file("ok", "ok.jpg")
            ),
            ".private" to listOf(file("inside", "inside.jpg"))
        )
        val queries = mutableListOf<String>()
        val seen = mutableListOf<String>()
        val result = walkPixivFolders(
            rootUri = "root",
            folderName = "Pixiv",
            previous = PixivWalkIndex(),
            list = { uri ->
                queries += uri
                listings[uri]
            },
            includeHidden = false,
            onItem = { entry, _, _ -> seen += entry.name }
        )
        assertEquals(listOf("root"), queries)
        assertEquals(listOf("ok.jpg"), seen)
        assertEquals(1, result.second.itemsFound)
    }
}
