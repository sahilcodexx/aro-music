package com.aro.music.data.innertube

import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import com.aro.music.data.model.Song

sealed interface HomeCatalogItem {
    val id: String
    val title: String
    val subtitle: String
    val artworkUrl: String?

    data class Track(val song: Song) : HomeCatalogItem {
        override val id get() = "song:${song.id}"
        override val title get() = song.title
        override val subtitle get() = song.displayArtist
        override val artworkUrl get() = song.albumArtUriString
    }
    data class AlbumItem(val album: Album) : HomeCatalogItem {
        override val id get() = "album:${album.id}"
        override val title get() = album.title
        override val subtitle get() = album.artist
        override val artworkUrl get() = album.albumArtUriString
    }
    data class ArtistItem(val artist: Artist) : HomeCatalogItem {
        override val id get() = "artist:${artist.id}"
        override val title get() = artist.name
        override val subtitle get() = ""
        override val artworkUrl get() = artist.imageUrl
    }
    data class PlaylistItem(val playlist: InnerTubePlaylist) : HomeCatalogItem {
        override val id get() = "playlist:${playlist.browseId}"
        override val title get() = playlist.title
        override val subtitle get() = playlist.author.orEmpty()
        override val artworkUrl get() = playlist.artworkUrl
    }
}

data class HomeCatalogSection(val title: String, val label: String?, val items: List<HomeCatalogItem>, val browseId: String? = null, val params: String? = null)
data class HomeCatalogPage(val sections: List<HomeCatalogSection>, val continuation: String?)

internal data class InnerTubeHomeSection(
    val title: String,
    val label: String?,
    val songs: List<InnerTubeTrack>,
    val albums: List<InnerTubeAlbum>,
    val artists: List<InnerTubeArtist>,
    val playlists: List<InnerTubePlaylist>,
    val browseId: String? = null,
    val params: String? = null,
)

data class ArtistCatalogPage(
    val sections: List<HomeCatalogSection>,
    val subscriberCount: String? = null,
    val monthlyAudience: String? = null,
    val description: String? = null,
)
