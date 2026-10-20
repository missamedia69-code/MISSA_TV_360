package com.missa.tv360.playback

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.missa.tv360.R

/**
 * Lecteur vidéo (Media3 ExoPlayer) — gère MP4 et HLS (live inclus).
 * Navigation au clavier/télécommande : Retour quitte, OK/Pause joue.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LIVE = "extra_live"
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"
        const val EXTRA_COVER_RES = "extra_cover_res"
        const val EXTRA_COVER_URL = "extra_cover_url"
    }

    private var player: ExoPlayer? = null
    private var titleOverlay: TextView? = null
    private var titleFade: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, R.string.player_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        title = intent.getStringExtra(EXTRA_TITLE)
        setupCover()
        setupTitleOverlay()
    }

    /**
     * Titre réel du contenu affiché en haut à gauche puis estompé (§9) :
     * le panneau reste « subtil » et ne masque jamais durablement la vidéo.
     */
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
        if (player == null) initializePlayer()
    }

    override fun onPause() {
        val p = player
        val id = intent.getStringExtra(EXTRA_VIDEO_ID)
        if (p != null && !id.isNullOrBlank()) {
            com.missa.tv360.data.WatchProgressStore.save(
                this, id, p.currentPosition, p.duration.coerceAtLeast(0L)
            )
        }
        super.onPause()
        releasePlayer()
    }

    /** Mode audio (radio/podcast) : affiche le visuel de fond à la place du noir. */
    private fun setupCover() {
        if (!intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)) return
        val playerView = findViewById<PlayerView>(R.id.player_view)
        playerView.useArtwork = true
        playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        val coverRes = intent.getIntExtra(EXTRA_COVER_RES, 0)
        if (coverRes != 0) {
            // Keep a local MISSA fallback visible while an optional remote cover loads.
            playerView.defaultArtwork = androidx.core.content.ContextCompat.getDrawable(
                this, coverRes
            )
        }
        intent.getStringExtra(EXTRA_COVER_URL)?.takeIf { it.isNotBlank() }?.let { url ->
            com.missa.tv360.ui.ThumbnailLoader.loadArtwork(url) { bitmap ->
                if (!isFinishing && !isDestroyed) {
                    playerView.defaultArtwork = android.graphics.drawable.BitmapDrawable(
                        resources, bitmap
                    )
                }
            }
        }
    }

    private fun initializePlayer() {
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val isLive = intent.getBooleanExtra(EXTRA_LIVE, false)

        val exo = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        if (isLive) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(5_000)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
        }

        // Reprise de la progression enregistrée (§9) : jamais sur un direct.
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        val resumeMs = if (isLive || videoId.isNullOrBlank()) {
            0L
        } else {
            com.missa.tv360.data.WatchProgressStore.position(this, videoId) ?: 0L
        }

        exo.setMediaItem(mediaItemBuilder.build(), resumeMs)
        exo.playWhenReady = true
        exo.prepare()

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, R.string.player_error, Toast.LENGTH_LONG).show()
                finish()
            }
        })

        findViewById<PlayerView>(R.id.player_view).player = exo
        player = exo
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onDestroy() {
        titleFade?.let { fade -> titleOverlay?.removeCallbacks(fade) }
        titleFade = null
        titleOverlay = null
        super.onDestroy()
    }
}
