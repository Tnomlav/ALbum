package com.example.album.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The R-18 filter uses the tag set the archiver already has in hand, so the
 * only thing that decides what gets hidden is this matcher.
 */
class AdultTagStoreTest {

    @Test
    fun adultTagsAreMatchedWhateverTheirCaseOrBrackets() {
        assertTrue(AdultTagStore.isAdultTag("R-18"))
        assertTrue(AdultTagStore.isAdultTag("r-18"))
        assertTrue(AdultTagStore.isAdultTag(" R-18 "))
        assertTrue(AdultTagStore.isAdultTag("【R-18】"))
        // R-18G is the harder variant of the same tag family.
        assertTrue(AdultTagStore.isAdultTag("R-18G"))
    }

    @Test
    fun ordinaryTagsAreNotAdult() {
        assertFalse(AdultTagStore.isAdultTag("オリジナル"))
        assertFalse(AdultTagStore.isAdultTag("R-18 ではない"))
        assertFalse(AdultTagStore.isAdultTag("18"))
        assertFalse(AdultTagStore.isAdultTag(""))
    }

    @Test
    fun anItemIsHiddenWhenEitherItsUriOrItsNameWasRecorded() {
        val filter = AdultTagFilter(
            uris = setOf("content://media/external/images/media/42"),
            names = setOf("illust_1_p0.jpg")
        )
        assertTrue(filter.contains("content://media/external/images/media/42", "renamed.jpg"))
        assertTrue(filter.contains("content://media/external/images/media/43", "illust_1_p0.jpg"))
        assertFalse(filter.contains("content://media/external/images/media/43", "ordinary.jpg"))
    }

    @Test
    fun anEmptyFilterKeepsEverything() {
        val filter = AdultTagFilter()
        assertTrue(filter.isEmpty)
        assertFalse(filter.contains("content://media/external/images/media/42", "illust_1_p0.jpg"))
    }
}
