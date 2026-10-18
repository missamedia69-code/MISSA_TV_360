package com.missa.tv360.playback

import android.os.Bundle
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
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LIVE = "extra_live"
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"
        const val EXTRA_COVER_RES = "extra_cover_res"
    }

    private var player: ExoPlayer? = null

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
    }

    override fun onResume() {
        super.onResume()
        if (player == null) initializePlayer()
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    /** Mode audio (radio/podcast) : affiche le visuel de fond à la place du noir. */
    private fun setupCover() {
        if (!intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)) return
        val coverRes = intent.getIntExtra(EXTRA_COVER_RES, 0)
        if (coverRes != 0) {
            findViewById<android.widget.ImageView>(R.id.player_cover).apply {
                setImageResource(coverRes)
                visibility = android.view.View.VISIBLE
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

        exo.setMediaItem(mediaItemBuilder.build())
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
}
