package com.aro.music.presentation.viewmodel

import com.aro.music.data.model.Song

/** Pins may deliberately overlap history; automatic quick picks never do. */
internal fun homeSpeedDial(
    pins: List<Song>, recentIds: Set<String>, favorites: List<Song>,
    frequent: List<Song>, quickPicks: List<Song>,
): List<Song> = (pins + (favorites + frequent + quickPicks).filterNot { it.id in recentIds })
    .distinctBy { it.id }.take(12)
