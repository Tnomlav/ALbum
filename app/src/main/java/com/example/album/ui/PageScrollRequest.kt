package com.example.album.ui

/**
 * A one shot request to put a lazy grid back on a known position.
 *
 * The grids keep their own scroll state, so lists that change length (a search
 * filter, a deletion) would otherwise clamp to whatever index they happened to
 * hold and leave the page at the bottom. [token] makes the request fire once:
 * the same index can be requested again by generating a new token.
 */
data class PageScrollRequest(
    val index: Int,
    val offset: Int,
    val token: Long,
    /**
     * Item to anchor on, when the grid can find it: the folder name for a
     * folder grid and the media URI for a media grid. Using the item instead
     * of the raw index keeps the page in place when the list shrinks (a delete
     * used to clamp the stored index to the new end and drop the user at the
     * bottom).
     */
    val key: String? = null
)
