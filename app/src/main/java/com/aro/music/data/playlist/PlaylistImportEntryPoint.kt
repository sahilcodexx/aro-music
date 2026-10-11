package com.aro.music.data.playlist

import com.aro.music.data.innertube.InnerTubeRepository
import com.aro.music.data.preferences.PlaylistPreferencesRepository
import com.aro.music.data.repository.MusicRepository
import com.aro.music.spotify.SpotifyRepository
import com.aro.music.spotify.SpotifySession
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaylistImportEntryPoint {
    fun importer(): PlaylistImportRepository
    fun youtube(): InnerTubeRepository
    fun spotify(): SpotifyRepository
    fun session(): SpotifySession
    fun playlists(): PlaylistPreferencesRepository
    fun music(): MusicRepository
}

