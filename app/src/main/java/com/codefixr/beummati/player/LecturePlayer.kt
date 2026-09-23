package com.codefixr.beummati.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LecturePlayer(context: Context) {
    private val player = ExoPlayer.Builder(context.applicationContext).build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _subtitle = MutableStateFlow("")
    val subtitle: StateFlow<String> = _subtitle.asStateFlow()

    fun play(url: String, title: String, subtitle: String) {
        _title.value = title
        _subtitle.value = subtitle
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        _isPlaying.value = true
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
        })
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
        _isPlaying.value = player.isPlaying
    }

    fun skip(seconds: Long) {
        val target = (player.currentPosition + seconds * 1000).coerceAtLeast(0)
        player.seekTo(target)
    }

    fun release() {
        player.release()
    }
}
