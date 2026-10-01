package com.example.album.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The recycle bin shows "days left" per entry and its total cost on disk. Both
 * are pure computations, so they are pinned here instead of on a device.
 */
class RecycleRetentionTest {
    private val day = RECYCLE_DAY_MILLIS

    @Test
    fun remainingDaysRoundsUpSoTheLastHoursStillReadAsADay() {
        assertEquals(60, recycleRemainingDays(deletedAt = 0L, retentionDays = 60, now = 0L))
        assertEquals(1, recycleRemainingDays(deletedAt = 0L, retentionDays = 60, now = 60 * day - 1))
        assertEquals(0, recycleRemainingDays(deletedAt = 0L, retentionDays = 60, now = 60 * day))
    }

    @Test
    fun expiredEntriesNeverReportNegativeDays() {
        assertEquals(0, recycleRemainingDays(deletedAt = 0L, retentionDays = 10, now = 30 * day))
    }

    @Test
    fun disabledRetentionReportsZero() {
        assertEquals(0, recycleRemainingDays(deletedAt = 0L, retentionDays = 0, now = 0L))
    }

    @Test
    fun usageCountsPrivateBackupsOnly() {
        val entries = listOf(
            recycleEntry(id = "a", storedPath = backupFile(120).absolutePath),
            recycleEntry(id = "b", storedPath = backupFile(30).absolutePath),
            // A system-Trash record has no private copy, so it costs nothing here.
            recycleEntry(id = "c", storedPath = "", systemTrashed = true),
            // Records whose backup vanished are filtered out by loadRecycleEntries.
            recycleEntry(id = "d", storedPath = File("definitely-missing-backup").absolutePath)
        )

        assertEquals(150L, privateRecycleBytes(entries))
    }

    private fun backupFile(bytes: Int): File =
        File.createTempFile("recycle-backup", ".bin").apply {
            deleteOnExit()
            writeBytes(ByteArray(bytes))
        }

    private fun recycleEntry(
        id: String,
        storedPath: String,
        systemTrashed: Boolean = false
    ) = RecycleEntry(
        id = id,
        sourceUri = "content://media/external/images/media/$id",
        storedPath = storedPath,
        originalName = "$id.jpg",
        originalFolder = "相册",
        originalRelativePath = "Pictures/相册/",
        mimeType = "image/jpeg",
        dateTaken = 0L,
        duration = 0L,
        isVideo = false,
        deletedAt = 0L,
        systemTrashed = systemTrashed
    )
}
