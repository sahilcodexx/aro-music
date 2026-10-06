package com.aro.music.spotify

/** Failed requests are results; cancellation still stops the caller's work. */
internal inline fun <T> spotifyRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Throwable) {
    if (error is kotlinx.coroutines.CancellationException) throw error
    Result.failure(error)
}
