package com.aro.music.data.innertube

import android.content.Context
import androidx.annotation.Keep
import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import com.aro.music.data.model.Song
import com.google.gson.Gson
import timber.log.Timber

/** A disk snapshot lets home survive process death without browsing the service again. */
internal class HomeFeedCache(context: Context) {
    private val preferences = context.getSharedPreferences("home_feed_snapshot", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun read(): Snapshot? = try {
        preferences.getString("snapshot_v1", null)?.let { gson.fromJson(it, Snapshot::class.java) }
            ?.takeIf { it.sections.isNotEmpty() || it.recommendations.isNotEmpty() }
    } catch (e: Exception) {
        Timber.w(e, "Ignoring invalid home snapshot")
        null
    }

    fun write(snapshot: Snapshot) {
        preferences.edit().putString("snapshot_v1", gson.toJson(snapshot)).apply()
    }

    @Keep data class Snapshot(
        val fetchedAt: Long,
        val sections: List<Section>,
        val recommendations: List<Section>,
        val seed: String?,
    ) {
        fun isFresh(now: Long = System.currentTimeMillis()) = now - fetchedAt in 0 until MAX_AGE_MS
    }
    @Keep data class Section(
        val title: String, val label: String?, val items: List<Item>,
        val browseId: String?, val params: String?,
    ) {
        fun toCatalog() = HomeCatalogSection(title, label, items.mapNotNull { it.toCatalog() }, browseId, params)
    }
    @Keep data class Item(
        val song: Song? = null, val album: Album? = null,
        val artist: Artist? = null, val playlist: InnerTubePlaylist? = null,
    ) {
        fun toCatalog(): HomeCatalogItem? = when {
            song != null -> HomeCatalogItem.Track(song)
            album != null -> HomeCatalogItem.AlbumItem(album)
            artist != null -> HomeCatalogItem.ArtistItem(artist)
            playlist != null -> HomeCatalogItem.PlaylistItem(playlist)
            else -> null
        }
    }
    companion object {
        const val MAX_AGE_MS = 6 * 60 * 60_000L
        fun encode(section: HomeCatalogSection) = Section(section.title, section.label, section.items.map {
            when (it) {
                is HomeCatalogItem.Track -> Item(song = it.song)
                is HomeCatalogItem.AlbumItem -> Item(album = it.album)
                is HomeCatalogItem.ArtistItem -> Item(artist = it.artist)
                is HomeCatalogItem.PlaylistItem -> Item(playlist = it.playlist)
            }
        }, section.browseId, section.params)
    }
}
