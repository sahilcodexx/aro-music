package com.aro.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aro.music.data.preferences.SavedLibraryRepository
import com.aro.music.data.model.Album
import com.aro.music.data.model.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SavedLibraryViewModel @Inject constructor(private val repository: SavedLibraryRepository) : ViewModel() {
    val albums = repository.albums.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val artists = repository.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hiddenSpotifyPlaylists = repository.hiddenSpotifyPlaylists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    fun toggleAlbum(album: Album) { viewModelScope.launch { repository.toggleAlbum(album) } }
    fun toggleArtist(artist: Artist) { viewModelScope.launch { repository.toggleArtist(artist) } }
    fun restoreSpotifyPlaylists() { viewModelScope.launch { repository.restoreSpotifyPlaylists() } }
}
