package com.blogspot.desymediaplayer

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.blogspot.desymediaplayer.databinding.ActivityPlayerBinding
import com.blogspot.desymediaplayer.utils.DoubleClickListener
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.util.Util
import android.view.animation.TranslateAnimation

import android.view.animation.Animation
import android.view.animation.RotateAnimation


private const val TAG = "ActivityPlayer"
private const val CURRENT_PLAYBACK_POSITION = "CurrentIndex"
private const val PLAYBACK_WINDOW_INDEX = "windowIndex"
private const val PLAY_IF_READY = "playWhenReady"
private const val MEDIA_URI = "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8"

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
    private val pbrBuffering: ProgressBar by lazy {
        findViewById(R.id.pbr_buffering)
    }
    private val subtitleTracks = ArrayList<String>()
    private val changeSubtitleButton:ImageButton by lazy {
        findViewById(R.id.ib_change_subtitle)
    }
    private val ffSeek:ImageButton by lazy{
        findViewById(R.id.exo_ffwd)
    }
    private val exoRew:ImageButton by lazy{
        findViewById(R.id.exo_rew)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useFullscreenUi()
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
                ) { dialog, _ ->
                    dialog.dismiss()
                }
                val cs: Array<CharSequence> = audioTracks.toArray(arrayOfNulls<CharSequence>(audioTracks.size))
                setItems(cs) { _, i ->
                    Toast.makeText(baseContext, "Changed audio to ${audioTracks[i]}", Toast.LENGTH_SHORT).show()
                    setAudioTracks(i)
                }
            }
            // Create the AlertDialog
            val alertDialog: AlertDialog = builder.create()
            alertDialog.show()
        }
        changeSubtitleButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.apply {
                setNegativeButton(
                    "Cancel"
                ) { dialog, _ ->
                    dialog.dismiss()
                }
                val cs: Array<CharSequence> = subtitleTracks.toArray(arrayOfNulls<CharSequence>(subtitleTracks.size))
                setItems(cs) { _, i ->
                    Toast.makeText(baseContext, "Subtitles changed to ${subtitleTracks[i]}", Toast.LENGTH_SHORT).show()
                    setSubtitleTrack(i)
                }
            }
            // Create the AlertDialog
            val alertDialog: AlertDialog = builder.create()
            alertDialog.show()
        }

    }

    private fun seekRight() {
        if (currentPlaybackPosition+5000 < player!!.duration) {
            currentPlaybackPosition += 5000
            player?.seekTo(currentPlaybackPosition)
        }

        val animation: Animation = TranslateAnimation(0F, 300F, 0F, 0F)
        animation.duration = 500
        ffSeek.startAnimation(animation)
    }

    private fun seekLeft() {
        if (currentPlaybackPosition-5000 >= 0) {
            currentPlaybackPosition -= 5000
            player?.seekTo(currentPlaybackPosition)
        }
        val animation: Animation = TranslateAnimation(0F, -300F, 0F, 0F)
        animation.duration = 500
        exoRew.startAnimation(animation)
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
        outState.putInt(PLAYBACK_WINDOW_INDEX, playerWindowIndex)
        outState.putLong(CURRENT_PLAYBACK_POSITION, currentPlaybackPosition)
        outState.putBoolean(PLAY_IF_READY, playWhenReady)
        super.onSaveInstanceState(outState, outPersistentState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        playerWindowIndex = savedInstanceState.getInt(PLAYBACK_WINDOW_INDEX)
        currentPlaybackPosition = savedInstanceState.getLong(CURRENT_PLAYBACK_POSITION)
        playWhenReady = savedInstanceState.getBoolean(PLAY_IF_READY)
    }

    private fun useFullscreenUi(){
        requestWindowFeature(Window.FEATURE_NO_TITLE) //will hide the title

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        ) //show the activity in full screen

        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun initializePlayer() {
        trackSelector = DefaultTrackSelector(this)
        player = SimpleExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer
                val mediaItem =
                    MediaItem.fromUri(MEDIA_URI)
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
            pbrBuffering.visibility = View.VISIBLE
            pauseButton.setImageResource(0)
            playButton.setImageResource(0)
        }

        private fun ready() {
            pbrBuffering.visibility = View.INVISIBLE
            pauseButton.setImageResource(R.drawable.exo_ic_pause_circle_filled)
            playButton.setImageResource(R.drawable.exo_ic_play_circle_filled)
            if(audioTracks.size == 0){
                getAudioTrack()
            }
            if (subtitleTracks.size == 0){
                getSubtitleTrack()
            }
        }
    }

    private fun getAudioTrack() {
        player?.apply {
            if (currentTrackGroups.length > 0) {
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

    private fun getSubtitleTrack() {
        player?.apply {
            if (currentTrackGroups.length > 0) {
                for (i in 0 until currentTrackGroups.length) {
                    val format = player!!.currentTrackGroups[i].getFormat(0).sampleMimeType
                    val lang = player!!.currentTrackGroups[i].getFormat(0).language
                    val id = player!!.currentTrackGroups[i].getFormat(0).id
                    if (format!!.contains("text") && id != null && lang != null) {
                        Log.i("Subtitle Tracks",format.toString())
                        Log.i("Subtitle Lang",lang)
                        subtitleTracks.add(lang)
                    }
                }
            }
        }
    }

    private fun setSubtitleTrack(i:Int){
        trackSelector.setParameters(
            trackSelector
                .buildUponParameters()
                .setMaxVideoSizeSd()
                .setForceHighestSupportedBitrate(true)
                .setPreferredTextLanguage(subtitleTracks[i])
        )
    }

}

