package com.blogspot.desymediaplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.blogspot.desymediaplayer.databinding.ActivityPlayerBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.util.Util

private const val TAG = "ActivityPlayer"
class PlayerActivity : AppCompatActivity() {
    private val viewBinding by lazy{
        ActivityPlayerBinding.inflate(layoutInflater)
    }
    private var player: SimpleExoPlayer? = null
    private var playerWindowIndex = 0
    private var currentPlaybackPosition = 0L
    private var playWhenReady = true
    private val playBackState = playbackStateListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24){
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT < 24 || player == null){
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (player!=null){
            releasePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    private fun initializePlayer(){
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setMaxVideoSizeSd()
                .setPreferredAudioLanguage("dubbed")
                .build()
        }
        player = SimpleExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer
                val mediaItem = MediaItem.fromUri("https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8")
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(playerWindowIndex,currentPlaybackPosition)
                exoPlayer.prepare()
                exoPlayer.addListener(playBackState)
            }
    }

    private fun releasePlayer(){
        player?.run {
            playerWindowIndex = this.currentWindowIndex
            currentPlaybackPosition = this.currentPosition
            this@PlayerActivity.playWhenReady = this.playWhenReady
            release()
        }
        player = null
    }


    private fun playbackStateListener() = object : Player.EventListener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                ExoPlayer.STATE_IDLE ->  viewBinding.pbrBuffering.visibility = View.INVISIBLE
                ExoPlayer.STATE_BUFFERING -> viewBinding.pbrBuffering.visibility = View.VISIBLE
                ExoPlayer.STATE_READY -> viewBinding.pbrBuffering.visibility = View.INVISIBLE
                ExoPlayer.STATE_ENDED ->  viewBinding.pbrBuffering.visibility = View.INVISIBLE
                else ->  viewBinding.pbrBuffering.visibility = View.INVISIBLE
            }
        }
    }
}

