package com.missa.tv360.playback

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.WatchProgressStore
import com.missa.tv360.ui.ThumbnailLoader
import com.missa.tv360.ui.fondParDefaut

/** Lecteur Media3 vidéo/audio avec reprise, chargement et erreur récupérable. */
class PlayerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LIVE = "extra_live"
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"
        const val EXTRA_COVER_RES = "extra_cover_res"
        const val EXTRA_COVER_URL = "extra_cover_url"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_HEADERS = "extra_headers"
        private const val TAG = "PlayerActivity"
        private const val USER_AGENT = "MISSA-TV-360/1.0 (Android player)"
        private const val STATE_POSITION = "state_position"
        private const val STATE_ERROR = "state_error"
    }

    private var player: ExoPlayer? = null
    private var titleOverlay: TextView? = null
    private var titleFade: Runnable? = null
    private var retryPositionMs = 0L
    private var hasFatalError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        retryPositionMs = savedInstanceState?.getLong(STATE_POSITION, 0L) ?: 0L
        hasFatalError = savedInstanceState?.getBoolean(STATE_ERROR, false) ?: false
        title = intent.getStringExtra(EXTRA_TITLE)
        setupCover()
        setupTitleOverlay()
        findViewById<Button>(R.id.player_retry).setOnClickListener { retryPlayback() }
        findViewById<Button>(R.id.player_back).setOnClickListener { finish() }
        findViewById<PlayerView>(R.id.player_view).apply {
            useController = true
            controllerShowTimeoutMs = 4_000
            isFocusable = true
        }
        if (hasFatalError) {
            showError(getString(R.string.player_error_source))
        } else if (intent.getStringExtra(EXTRA_URL).isNullOrBlank()) {
            showError(getString(R.string.player_error_source))
        }
    }

    private fun setupTitleOverlay() {
        val label = intent.getStringExtra(EXTRA_TITLE)?.trim()
        if (label.isNullOrEmpty()) return
        val overlay = findViewById<TextView>(R.id.player_title)
        overlay.text = label
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f
        val fade = Runnable { overlay.animate().alpha(0f).setDuration(300L).start() }
        titleOverlay = overlay
        titleFade = fade
        overlay.postDelayed(fade, 4_000L)
    }

    override fun onResume() {
        super.onResume()
        if (player == null && !hasFatalError) initializePlayer()
    }

    override fun onPause() {
        saveProgress()
        super.onPause()
        releasePlayer()
    }

    private fun saveProgress() {
        val current = player ?: return
        val id = intent.getStringExtra(EXTRA_VIDEO_ID)
        if (id.isNullOrBlank() || intent.getBooleanExtra(EXTRA_LIVE, false)) return
        retryPositionMs = current.currentPosition.coerceAtLeast(0L)
        WatchProgressStore.save(
            this,
            id,
            retryPositionMs,
            current.duration.coerceAtLeast(0L),
        )
        CatalogStore.refreshUserRows(this)
    }

    private fun setupCover() {
        if (!intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)) return
        val playerView = findViewById<PlayerView>(R.id.player_view)
        playerView.useArtwork = true
        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        val coverRes = intent.getIntExtra(EXTRA_COVER_RES, 0)
        if (coverRes != 0) {
            playerView.defaultArtwork = ContextCompat.getDrawable(this, coverRes)
        }
        intent.getStringExtra(EXTRA_COVER_URL)?.takeIf { it.isNotBlank() }?.let { url ->
            ThumbnailLoader.loadArtwork(url) { bitmap ->
                if (!isFinishing && !isDestroyed) {
                    playerView.defaultArtwork = BitmapDrawable(resources, bitmap)
                }
            }
        }
    }

    private fun initializePlayer(startPositionMs: Long? = null) {
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            showError(getString(R.string.player_error_source))
            return
        }
        hasFatalError = false
        findViewById<View>(R.id.player_error_panel).isVisible = false
        findViewById<View>(R.id.player_loading).isVisible = true
        val isLive = intent.getBooleanExtra(EXTRA_LIVE, false)
        val format = intent.getStringExtra(EXTRA_FORMAT)
        @Suppress("UNCHECKED_CAST")
        val requestHeaders = (intent.getSerializableExtra(EXTRA_HEADERS) as? HashMap<String, String>).orEmpty()
        Log.i(
            TAG,
            "playbackStart source=${safeUrlSummary(url)} format=${format ?: "auto"} " +
                "live=$isLive headerNames=${requestHeaders.keys.joinToString(",")}"
        )
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        mimeTypeFor(intent.getStringExtra(EXTRA_FORMAT))?.let(mediaItemBuilder::setMimeType)
        if (isLive) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(5_000)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build(),
            )
        }
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        val storedPosition = if (isLive || videoId.isNullOrBlank()) 0L
        else WatchProgressStore.position(this, videoId) ?: 0L
        val resumeMs = if (isLive) 0L else startPositionMs ?: storedPosition
        exo.setMediaItem(mediaItemBuilder.build(), resumeMs)
        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    findViewById<View>(R.id.player_loading).isVisible = false
                } else if (playbackState == Player.STATE_BUFFERING && !hasFatalError) {
                    findViewById<View>(R.id.player_loading).isVisible = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                retryPositionMs = exo.currentPosition.coerceAtLeast(0L)
                val httpError = findHttpResponseCode(error)
                Log.e(
                    TAG,
                    "playbackError source=${safeUrlSummary(url)} " +
                        "failedRequest=${httpError?.let { safeUrlSummary(it.dataSpec.uri.toString()) } ?: "unknown"} " +
                        "httpCode=${httpError?.responseCode ?: "none"} format=${format ?: "auto"} " +
                        "errorCode=${error.errorCode} cause=${rootCauseSummary(error)}",
                    error,
                )
                showError(errorMessage(error))
            }
        })
        findViewById<PlayerView>(R.id.player_view).player = exo
        player = exo
        exo.prepare()
    }

    private fun retryPlayback() {
        val position = retryPositionMs
        releasePlayer()
        initializePlayer(position)
    }

    private fun showError(message: String) {
        hasFatalError = true
        findViewById<View>(R.id.player_loading).isVisible = false
        findViewById<View>(R.id.player_error_panel).isVisible = true
        findViewById<TextView>(R.id.player_error_message).text = message
        findViewById<Button>(R.id.player_retry).requestFocus()
    }

    private fun errorMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> getString(R.string.player_error_network)
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        -> getString(R.string.player_error_format)
        else -> getString(R.string.player_error_source)
    }

    private fun mimeTypeFor(format: String?): String? = when (format?.lowercase()) {
        "hls" -> MimeTypes.APPLICATION_M3U8
        "dash" -> MimeTypes.APPLICATION_MPD
        "mp4" -> MimeTypes.VIDEO_MP4
        "mp3" -> MimeTypes.AUDIO_MPEG
        else -> null
    }

    /**
     * Résumé destiné aux journaux : ne jamais écrire de jeton ou de query string
     * d'une URL vidéo temporaire.
     */
    private fun safeUrlSummary(raw: String?): String {
        val uri = Uri.parse(raw.orEmpty())
        val host = uri.host ?: return "invalid-uri"
        val lastSegment = uri.lastPathSegment.orEmpty()
        val extension = lastSegment.substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{2,6}")) }
            ?: "none"
        return "${uri.scheme ?: "unknown"}://$host segments=${uri.pathSegments.size} " +
            "extension=$extension hasQuery=${!uri.query.isNullOrEmpty()}"
    }

    private fun findHttpResponseCode(error: Throwable): HttpDataSource.InvalidResponseCodeException? {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth++ < 20) {
            if (current is HttpDataSource.InvalidResponseCodeException) return current
            current = current.cause
        }
        return null
    }

    private fun rootCauseSummary(error: Throwable): String {
        var current: Throwable = error
        var depth = 0
        while (current.cause != null && depth++ < 20) current = current.cause!!
        val message = current.message.orEmpty().replace(Regex("\\s+"), " ").take(180)
        return "${current::class.java.simpleName}:$message"
    }

    private fun createDataSourceFactory(): DefaultHttpDataSource.Factory {
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        @Suppress("UNCHECKED_CAST")
        val headers = intent.getSerializableExtra(EXTRA_HEADERS) as? HashMap<String, String>
        if (!headers.isNullOrEmpty()) {
            factory.setDefaultRequestProperties(headers)
        }
        return factory
    }

    private fun releasePlayer() {
        findViewById<PlayerView>(R.id.player_view).player = null
        player?.release()
        player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        player?.let { retryPositionMs = it.currentPosition.coerceAtLeast(0L) }
        outState.putLong(STATE_POSITION, retryPositionMs)
        outState.putBoolean(STATE_ERROR, hasFatalError)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        titleFade?.let { fade -> titleOverlay?.removeCallbacks(fade) }
        titleFade = null
        titleOverlay = null
        super.onDestroy()
    }
}
