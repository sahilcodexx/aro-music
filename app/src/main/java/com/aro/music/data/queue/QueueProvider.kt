package com.aro.music.data.queue

import androidx.media3.common.MediaItem

/**
 * Supplies the queue for a playback session, including the pages that come after
 * it.
 *
 * Ported from Meld's `com.metrolist.music.playback.queues.Queue`, which is the
 * piece that makes its queue feel alive: the player's timeline holds what is
 * already known, and a `Queue` is only the *source* that can extend it. That
 * split is what lets playback start instantly while the rest of a radio or
 * playlist is still being fetched, and it is why the queue is not simply "the
 * search results".
 *
 * Implementations must be safe to call from the main thread and do their IO in
 * their own coroutines.
 */
interface QueueProvider {

    /**
     * Item to hand to the player *immediately*, before [getInitialStatus]
     * returns.
     *
     * This is what removes the startup gap: the tapped track plays while the
     * rest of the radio is still loading. `null` when there is nothing to
     * preload.
     */
    val preloadItem: MediaItem?

    /** First page of the queue, with [Status.mediaItemIndex] at the start position. */
    suspend fun getInitialStatus(): Status

    /** Full source when it is cheaply available, so shuffle can cover everything. */
    suspend fun getFullStatus(): Status? = null

    /** True while [nextPage] can still return more items. */
    fun hasNextPage(): Boolean

    /** Next page of items, or an empty list when exhausted. */
    suspend fun nextPage(): List<MediaItem>

    /** Called before shuffling so implementations can pull the rest of the source. */
    suspend fun shuffleRemainingTracks() {}

    /**
     * A page of queue items.
     *
     * @param title display title for the queue, e.g. the playlist or radio name
     * @param items the page of tracks
     * @param mediaItemIndex where playback should start inside [items]
     * @param position start position in milliseconds
     */
    data class Status(
        val title: String? = null,
        val items: List<MediaItem> = emptyList(),
        val mediaItemIndex: Int = 0,
        val position: Long = 0L,
    )
}

/** A fixed in-memory list. Used for search results, albums and local playlists. */
class ListQueue(
    override val preloadItem: MediaItem? = null,
    private val title: String? = null,
    private val items: List<MediaItem>,
    private val startIndex: Int = 0,
    private val position: Long = 0L,
) : QueueProvider {

    override suspend fun getInitialStatus(): QueueProvider.Status =
        QueueProvider.Status(title, items, startIndex, position)

    /** A fixed list never grows, so it has no next page. */
    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}

/** Nothing to play. */
object EmptyQueue : QueueProvider {
    override val preloadItem: MediaItem? = null
    override suspend fun getInitialStatus(): QueueProvider.Status =
        QueueProvider.Status(null, emptyList(), -1)

    override fun hasNextPage(): Boolean = false
    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
