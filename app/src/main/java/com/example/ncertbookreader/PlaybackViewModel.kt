package com.example.ncertbookreader

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

data class PlaybackUiState(
    val currentAudioPath: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null
)

class PlaybackViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var mediaBrowser: MediaBrowser? = null
    private lateinit var browserFuture: ListenableFuture<MediaBrowser>

    fun initializeBrowser(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlayerService::class.java))
        browserFuture = MediaBrowser.Builder(context, sessionToken)
            .buildAsync()

        browserFuture.addListener({
            try {
                mediaBrowser = browserFuture.get()
                mediaBrowser?.addListener(playerListener)
                updatePlayerState() // Initial state update
            } catch (e: ExecutionException) {
                // Handle error, e.g., service not found or connection refused
                 _uiState.value = _uiState.value.copy(errorMessage = "Error connecting to media service: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                startProgressUpdater()
            } else {
                stopProgressUpdater()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.value = _uiState.value.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING,
                isPlaying = playbackState == Player.STATE_READY && mediaBrowser?.isPlaying == true
            )
            if (playbackState == Player.STATE_READY) {
                _uiState.value = _uiState.value.copy(totalDurationMs = mediaBrowser?.duration ?: 0L)
            }
             if (playbackState == Player.STATE_ENDED) {
                _uiState.value = _uiState.value.copy(isPlaying = false, currentPositionMs = 0L) // Reset on end
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaMetadata?.extras?.getString("audioPath")?.let { path ->
                _uiState.value = _uiState.value.copy(
                    currentAudioPath = path,
                    totalDurationMs = mediaBrowser?.duration ?: 0L,
                    currentPositionMs = 0L // Reset position for new item
                )
            }
        }
         override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.value = _uiState.value.copy(errorMessage = "Player error: ${error.message}", isPlaying = false)
        }
    }

    private fun startProgressUpdater() {
        viewModelScope.launch {
            while (_uiState.value.isPlaying) {
                _uiState.value = _uiState.value.copy(currentPositionMs = mediaBrowser?.currentPosition ?: 0L)
                kotlinx.coroutines.delay(500) // Update twice a second
            }
        }
    }

    private fun stopProgressUpdater() {
        // The coroutine will stop itself when isPlaying becomes false
    }


    fun playAudio(context: Context, audioPath: String, title: String) {
        if (mediaBrowser == null) {
            initializeBrowser(context) // Ensure browser is initialized
            // Consider delaying playback until browser is connected or queuing the request
             _uiState.value = _uiState.value.copy(errorMessage = "Media controller not available yet. Please wait.")
            return
        }

        val assetUri = Uri.parse("asset:///$audioPath") // Assumes audioPath is relative to assets root

        val mediaItem = MediaItem.Builder()
            .setMediaId(audioPath) // Use audioPath as a unique ID
            .setUri(assetUri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("NCERT Book Reader") // Optional: set artist or other metadata
                    .setExtras(android.os.Bundle().apply { putString("audioPath", audioPath) })
                    .build()
            )
            .build()

        mediaBrowser?.setMediaItem(mediaItem)
        mediaBrowser?.prepare()
        mediaBrowser?.play()
        _uiState.value = _uiState.value.copy(currentAudioPath = audioPath, isPlaying = true, totalDurationMs = 0L, currentPositionMs = 0L) // Optimistic update
        if (mediaBrowser?.isPlaying == true) startProgressUpdater() // Ensure progress updater starts
    }

    fun pauseAudio() {
        mediaBrowser?.pause()
         _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    fun resumeAudio() {
        mediaBrowser?.play()
        _uiState.value = _uiState.value.copy(isPlaying = true)
        if (mediaBrowser?.isPlaying == true) startProgressUpdater()
    }

    fun seekAudio(positionMs: Long) {
        mediaBrowser?.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs) // Optimistic update
    }

    private fun updatePlayerState() {
        mediaBrowser?.let { browser ->
            val currentMediaItem = browser.currentMediaItem
            _uiState.value = _uiState.value.copy(
                currentAudioPath = currentMediaItem?.mediaMetadata?.extras?.getString("audioPath"),
                isPlaying = browser.isPlaying,
                currentPositionMs = browser.currentPosition,
                totalDurationMs = browser.duration,
                isBuffering = browser.playbackState == Player.STATE_BUFFERING
            )
            if (browser.isPlaying) {
                startProgressUpdater()
            }
        }
    }

    override fun onCleared() {
        mediaBrowser?.removeListener(playerListener)
        mediaBrowser?.let { MediaBrowser.releaseFuture(browserFuture) }
        super.onCleared()
    }
}
