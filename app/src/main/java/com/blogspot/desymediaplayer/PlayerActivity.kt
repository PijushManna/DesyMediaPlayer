package com.blogspot.desymediaplayer

import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.desymediaplayer.databinding.ActivityPlayerBinding
import com.blogspot.desymediaplayer.utils.DoubleClickListener
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.util.Util


private const val TAG = "ActivityPlayer"
private const val CURRENT_PLAYBACK_POSITION = "CurrentIndex"
private const val PLAYBACK_WINDOW_INDEX = "windowIndex"
private const val PLAY_IF_READY = "playWhenReady"

class PlayerActivity : AppCompatActivity() {

    private val viewBinding by lazy {
        ActivityPlayerBinding.inflate(layoutInflater)
    }
    private var player: SimpleExoPlayer? = null
    private var playerWindowIndex = 0
    private var currentPlaybackPosition = 0L
    private var playWhenReady = true
    private val playBackState = playbackStateListener()
    private lateinit var trackSelector: DefaultTrackSelector
    private val audioTracks = ArrayList<String>()
    private val changeAudioButton: ImageButton by lazy {
        findViewById(R.id.ib_change_audio)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        val right: ImageButton = findViewById(R.id.ib_seek_right)
        val left: ImageButton = findViewById(R.id.ib_seek_left)

        right.setOnClickListener(object : DoubleClickListener() {
            override fun onDoubleClick(v: View?) {
                seekRight()
            }
        })
        left.setOnClickListener(object : DoubleClickListener() {
            override fun onDoubleClick(v: View?) {
                seekLeft()
            }
        })
        changeAudioButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.apply {
                setNegativeButton(
                    "Cancel"
                ) { dialog, id ->
                    dialog.dismiss()
                }
                val cs: Array<CharSequence> = audioTracks.toArray(arrayOfNulls<CharSequence>(audioTracks.size))
                setItems(cs) { _, i ->
                    Toast.makeText(baseContext, i.toString(), Toast.LENGTH_SHORT).show()
                    setAudioTracks(i)
                }
            }
            // Create the AlertDialog
            val alertDialog: AlertDialog = builder.create()
            alertDialog.show()
        }
    }

    private fun seekRight() {
        player?.seekForward()
    }

    private fun seekLeft() {
        player?.seekBack()
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT < 24 || player == null) {
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (player != null) {
            releasePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putInt(PLAYBACK_WINDOW_INDEX, playerWindowIndex)
        outState.putLong(CURRENT_PLAYBACK_POSITION, currentPlaybackPosition)
        outState.putBoolean(PLAY_IF_READY, playWhenReady)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        playerWindowIndex = savedInstanceState.getInt(PLAYBACK_WINDOW_INDEX)
        currentPlaybackPosition = savedInstanceState.getLong(CURRENT_PLAYBACK_POSITION)
        playWhenReady = savedInstanceState.getBoolean(PLAY_IF_READY)
    }

    private fun initializePlayer() {
        trackSelector = DefaultTrackSelector(this)
        player = SimpleExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer
                val mediaItem =
                    MediaItem.fromUri("https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8")
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(playerWindowIndex, currentPlaybackPosition)
                exoPlayer.prepare()
                exoPlayer.addListener(playBackState)
            }
    }

    private fun releasePlayer() {
        player?.run {
            playerWindowIndex = this.currentWindowIndex
            currentPlaybackPosition = this.currentPosition
            this@PlayerActivity.playWhenReady = this.playWhenReady
            release()
        }
        player = null
    }

    private fun playbackStateListener() = object : Player.EventListener {
        private val pauseButton by lazy {
            findViewById<ImageButton>(R.id.exo_pause)
        }
        private val playButton by lazy {
            findViewById<ImageButton>(R.id.exo_play)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                ExoPlayer.STATE_IDLE -> Log.d(TAG, "State Idle")
                ExoPlayer.STATE_BUFFERING -> buffering()
                ExoPlayer.STATE_READY -> ready()
                ExoPlayer.STATE_ENDED -> Log.d(TAG, "State Ended")
                else -> Log.d(TAG, "State Unknown")
            }
        }

        private fun buffering() {
            viewBinding.pbrBuffering.visibility = View.VISIBLE
            pauseButton.setImageResource(0)
            playButton.setImageResource(0)
        }

        private fun ready() {
            viewBinding.pbrBuffering.visibility = View.INVISIBLE
            pauseButton.setImageResource(R.drawable.exo_ic_pause_circle_filled)
            playButton.setImageResource(R.drawable.exo_ic_play_circle_filled)
            getAudioTrack()
        }
    }

    private fun getAudioTrack() {
        Log.i("PlayerAudio", audioTracks.toString())


        player?.apply {
            if (currentTrackGroups.length > 0) {
                audioTracks.clear()
                for (i in 0 until currentTrackGroups.length) {
                    val format = player!!.currentTrackGroups[i].getFormat(0).sampleMimeType
                    val lang = player!!.currentTrackGroups[i].getFormat(0).language
                    val id = player!!.currentTrackGroups[i].getFormat(0).id
                    if (format!!.contains("audio") && id != null && lang != null) {
                        audioTracks.add(lang)
                    }
                }
            }
        }
    }

    private fun setAudioTracks(i:Int){
        trackSelector.setParameters(
            trackSelector
                .buildUponParameters()
                .setMaxVideoSizeSd()
                .setForceHighestSupportedBitrate(true)
                .setPreferredAudioLanguage(audioTracks[i])
        )
    }
}

