package com.example.mediaplayer

import android.R
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSessionManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.mediaplayer.AudioDetailsActivity.Companion.checkRepeat
import com.example.mediaplayer.AudioDetailsActivity.Companion.ctime
import com.example.mediaplayer.AudioDetailsActivity.Companion.name
import com.example.mediaplayer.AudioDetailsActivity.Companion.palyPause
import com.example.mediaplayer.AudioDetailsActivity.Companion.seekbar
import com.example.mediaplayer.AudioDetailsActivity.Companion.totalTime
import com.example.mediaplayer.Util.Companion.ACTION_NEXT
import com.example.mediaplayer.Util.Companion.ACTION_PAUSE
import com.example.mediaplayer.Util.Companion.ACTION_PLAY
import com.example.mediaplayer.Util.Companion.ACTION_PREVIOUS
import com.example.mediaplayer.Util.Companion.ACTION_STOP
import com.example.mediaplayer.Util.Companion.Broadcast_PLAY_NEW_AUDIO
import com.example.mediaplayer.Util.Companion.CHANNEL_ID
import java.io.IOException


class MediaPlayerService : Service(), MediaPlayer.OnCompletionListener,
    MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
    MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,
    AudioManager.OnAudioFocusChangeListener {

    //List of available Audio files
    private var audioList: ArrayList<Audio>? = null
    private var audioIndex = -1
    private var activeAudio: Audio? = null  //an object of the currently playing audio
    private var binder = MediaPlayerBinder()
    var mediaPlayer: MediaPlayer? = null;
    private lateinit var audioManager: AudioManager

    lateinit var activityIntent: Intent


    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    //MediaSession
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var transportControls: MediaControllerCompat.TransportControls? = null

    //AudioPlayer notification ID
    private val NOTIFICATION_ID = 101

    //Handle incoming phone calls
    private var ongoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null

    //Used to pause/resume MediaPlayer
    private var resumePosition: Int = 0

    //UI
    companion object {

    }

    enum class PlaybackStatus {
        PLAYING, PAUSED
    }

    override fun onCreate() {
        super.onCreate()
        //create notification channel
        // Perform one-time setup procedures , Manage incoming phone calls during playback, Pause MediaPlayer on incoming call, Resume on hangup.
        callStateListener()
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver()
        //Listen for new Audio to play my BroadcastReceiver
        registerPlayNewAudio()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            //Load data from SharedPreferences
            val storage = AudioStorage(applicationContext)
            audioList = storage.loadAudioList()
            audioIndex = storage.loadAudioIndex()
            handler = Handler()

            activityIntent = Intent(this@MediaPlayerService, AudioDetailsActivity::class.java)
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            if (audioIndex != -1 && audioIndex < audioList!!.size) {
                //index is in a valid range
                activeAudio = audioList!![audioIndex]
            } else {
                stopSelf()
            }
        } catch (e: NullPointerException) {
            stopSelf()
        }

        //Request audio focus
        if (!requestAudioFocus())
            stopSelf() //Could not gain focus

        if (mediaSessionManager == null) {
            try {
                initMediaSession()
                initMediaPlayer()
            } catch (e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(PlaybackStatus.PLAYING)
        }
        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    inner class MediaPlayerBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    override fun onCompletion(mp: MediaPlayer?) {
        //Invoked when playback of a media source has completed.
        if (checkRepeat == 1) {
            //reset mediaPlayer
            mediaPlayer?.reset()
            initMediaPlayer()
            //chang notification
            buildNotification(PlaybackStatus.PLAYING)
        } else if (checkRepeat == 2) {
            skipToNext()
        } else {

            stopMedia()
        }
        //stop the service
        //stopSelf()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        playMedia()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        when (what) {
            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK ->
                Log.d(
                    "MediaPlayer Error",
                    "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra
                )
            MediaPlayer.MEDIA_ERROR_SERVER_DIED ->
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra)
            MediaPlayer.MEDIA_ERROR_UNKNOWN ->
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
        }
        return false
    }

    override fun onSeekComplete(mp: MediaPlayer?) {
        mediaPlayer = mp
    }

    override fun onInfo(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
        TODO("Not yet implemented")
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // resume playback
                if (mediaPlayer == null) initMediaPlayer()
                else if (mediaPlayer?.isPlaying == false)
                    mediaPlayer?.start()
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer?.isPlaying != false)
                    pauseMedia()
//                    mediaPlayer?.stop()
//                mediaPlayer?.release()
//                mediaPlayer = null
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer?.isPlaying != false)
                    pauseMedia()
                // mediaPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer?.isPlaying() == true)
                    mediaPlayer?.setVolume(0.1f, 0.1f)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        Log.i("requestAudioFocus", "RUNNING")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result: Int = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            return true //Focus
        //Could not gain focus
        return false
    }

    private fun removeAudioFocus(): Boolean {
        Log.i("removeAudioFocus", "RUNNING")
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this@MediaPlayerService)
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()
        //Set up MediaPlayer event listeners
        mediaPlayer?.apply {
            setOnCompletionListener(this@MediaPlayerService)
            setOnPreparedListener(this@MediaPlayerService)
            setOnErrorListener(this@MediaPlayerService)
            setOnBufferingUpdateListener(this@MediaPlayerService)
            setOnInfoListener(this@MediaPlayerService)
            setOnSeekCompleteListener(this@MediaPlayerService)
            //Reset so that the MediaPlayer is not pointing to another data source
            reset()
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            try {
                setDataSource(activeAudio?.data)
            } catch (e: IOException) {
                stopSelf()
            }
            prepareAsync()
        }
    }

    fun playing() {
        mediaPlayer?.currentPosition?.let { seekbar.progress = it }
        if (mediaPlayer?.isPlaying!!) {
            runnable = Runnable {
                ctime.text = createTimeLabel(mediaPlayer!!.currentPosition)
                playing()
            }
            handler.postDelayed(runnable, 1000)
        }
    }

    private fun playMedia() {
        seekbar.max = mediaPlayer?.duration!!
        totalTime.text = createTimeLabel(mediaPlayer?.duration!!)
        if (mediaPlayer?.isPlaying == false)
            mediaPlayer?.start()
        playing()

    }

    fun createTimeLabel(duration: Int): String? {
        var timeLabel: String? = ""
        val min = duration / 1000 / 60
        val sec = duration / 1000 % 60
        timeLabel += "$min:"
        if (sec < 10) timeLabel += "0"
        timeLabel += sec
        return timeLabel
    }

    private fun stopMedia() {
        palyPause.setImageResource(com.example.mediaplayer.R.drawable.play)
        if (mediaPlayer == null)
            return
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            //stopSelf()
        }

    }

    fun pauseMedia() {
        palyPause.setImageResource(com.example.mediaplayer.R.drawable.play)
        if (mediaPlayer?.isPlaying != false) {
            mediaPlayer?.pause()
            resumePosition = mediaPlayer?.currentPosition!!
        }
        buildNotification(PlaybackStatus.PAUSED)
    }

    fun resumeMedia() {
        palyPause.setImageResource(com.example.mediaplayer.R.drawable.pause)
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.seekTo(resumePosition)
            mediaPlayer?.start()
            playing()
        }
        buildNotification(PlaybackStatus.PLAYING)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer != null) {
            stopMedia()
            mediaPlayer?.release()
            mediaPlayer = null
        }
        removeAudioFocus()
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
        removeNotification()
        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);
        //clear cached playlist
        AudioStorage(getApplicationContext()).clearData()
    }


    //Becoming noisy
    private val becomingNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia()
            buildNotification(PlaybackStatus.PAUSED)
        }
    }

    private fun registerBecomingNoisyReceiver() {
        //register after getting audio focus
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, intentFilter)
    }

    //Handle incoming phone calls
    private fun callStateListener() {
        // Get the telephony manager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        //Starting listening for PhoneState changes
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING ->
                        if (mediaPlayer != null) {
                            pauseMedia()
                            ongoingCall = true
                        }
                    TelephonyManager.CALL_STATE_IDLE ->
                        // Phone normal. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                }
            }
        }
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager!!.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private val playNewAudio: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            //Get the new media index form SharedPreferences
            audioIndex = AudioStorage(getApplicationContext()).loadAudioIndex()
            if (audioIndex != -1 && audioIndex < audioList!!.size) {
                //index is in a valid range
                activeAudio = audioList?.get(audioIndex)
            } else {
                stopSelf()
            }

            stopMedia()
            mediaPlayer?.reset()
            initMediaPlayer()
            updateMetaData()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun registerPlayNewAudio() {
        //Register playNewMedia receiver
        val filter = IntentFilter(Broadcast_PLAY_NEW_AUDIO)
        registerReceiver(playNewAudio, filter)
    }

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            resumeMedia()
        }

        override fun onPause() {
            pauseMedia()
        }

        override fun onSkipToNext() {
            updateMetaData()
            skipToNext()
        }

        override fun onSkipToPrevious() {
            updateMetaData()
            skipToPrevious()
        }

        override fun onStop() {
            removeNotification()
            //Stop the service
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            // resumePosition= pos.toInt()
        }
    }

    @Throws(RemoteException::class)
    private fun initMediaSession() {
        if (mediaSessionManager != null) return  //mediaSessionManager exists
        mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager?
        // Create a new MediaSession
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        //Get MediaSessions transport controls
        transportControls = mediaSession?.controller?.transportControls
        //set MediaSession -> ready to receive media commands
        mediaSession?.setActive(true)
        //indicate that the MediaSession handles transport control commands
        mediaSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        //Set mediaSession's MetaData
        updateMetaData()
        mediaSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        mediaSession?.setCallback(callback)
    }

    private fun updateMetaData() {
        val albumArt: Bitmap = BitmapFactory.decodeResource(
            resources,
            com.example.mediaplayer.R.drawable.ic_music
        ) // medias albumArt
        // Update the current metadata
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio!!.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio!!.album)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio!!.title)
                .build()
        )
    }

    fun skipToNext() {
        palyPause.setImageResource(com.example.mediaplayer.R.drawable.pause)
        if (audioIndex == audioList!!.size - 1) {
            //if last in playlist
            audioIndex = 0
            activeAudio = audioList!!.get(audioIndex)
        } else {
            //get next in playlist
            activeAudio = audioList!!.get(++audioIndex)
        }
        //update textview of title
        name.text = activeAudio?.title
        //Update stored index
        AudioStorage(getApplicationContext()).storeAudioIndex(audioIndex)
        stopMedia()
        //reset mediaPlayer
        mediaPlayer?.reset()
        initMediaPlayer()
        //chang notification
        buildNotification(PlaybackStatus.PLAYING)

    }

    fun skipToPrevious() {
        palyPause.setImageResource(com.example.mediaplayer.R.drawable.pause)
        //if last in playlist
        if (audioIndex == 0) {
            audioIndex = audioList!!.size - 1
            activeAudio = audioList!!.get(audioIndex)
        }
        //get next in playlist
        else {
            activeAudio = audioList!!.get(--audioIndex)
        }
        //update textview of title
        name.text = activeAudio?.title
        //Update stored index
        AudioStorage(getApplicationContext()).storeAudioIndex(audioIndex)
        stopMedia()
        //reset mediaPlayer
        mediaPlayer?.reset()
        initMediaPlayer()
        //chang notification
        buildNotification(PlaybackStatus.PLAYING)

    }

    private fun buildNotification(playbackStatus: PlaybackStatus) {
        var actionIntent: PendingIntent? = null
        val homeIntent: PendingIntent =
            PendingIntent.getActivity(this, 1, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        var notificationAction = R.drawable.ic_media_pause //needs to be initialized
        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = R.drawable.ic_media_pause;
            //create the pause action
            actionIntent = playbackAction(1)
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = R.drawable.ic_media_play
            //create the play action
            actionIntent = playbackAction(0)
        }

        val largeIcon: Bitmap = BitmapFactory.decodeResource(
            resources,
            com.example.mediaplayer.R.drawable.ic_music
        ) // medias albumArt
        // Create a new Notification
        val notificationBuilder: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            // Show controls on lock screen even when user hides sensitive content.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.stat_sys_headset)
            //can't remove notification
            .setOngoing(true)
            // Set the Notification color
            .setColor(getResources().getColor(R.color.background_dark))
            // Set the large and small icons
            // Add playback actions
            .addAction(R.drawable.ic_media_previous, "previous", playbackAction(3))
            .addAction(notificationAction, "play_pause", actionIntent)
            .addAction(R.drawable.ic_media_next, "next", playbackAction(2))
            .setContentIntent(homeIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession?.getSessionToken())
            )
            // Set Notification content information
            .setContentTitle(activeAudio?.album)
            .setContentText(activeAudio?.artist)
            .setContentInfo(activeAudio?.title)
            .setLargeIcon(largeIcon)
            .build()
        if (notificationBuilder != null) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                NOTIFICATION_ID,
                notificationBuilder
            )//.build())
        }
    }

    private fun removeNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(
            NOTIFICATION_ID
        )
    }

    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackAction = Intent(this, MediaPlayerService::class.java)
        when (actionNumber) {
            0 -> {
                // Play
                playbackAction.action = ACTION_PLAY
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            1 -> {
                // Pause
                playbackAction.action = ACTION_PAUSE
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            2 -> {
                // Next track
                playbackAction.action = ACTION_NEXT
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            3 -> {
                // Previous track
                playbackAction.action = ACTION_PREVIOUS
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
        }
        return null
    }

    private fun handleIncomingActions(playbackAction: Intent?) {
        if (playbackAction == null || playbackAction.action == null) return
        val actionString = playbackAction.action
        if (actionString.equals(ACTION_PLAY, ignoreCase = true)) {
            transportControls!!.play()
            palyPause.setImageResource(com.example.mediaplayer.R.drawable.pause)
            resumeMedia()
        } else if (actionString.equals(ACTION_PAUSE, ignoreCase = true)) {
            transportControls!!.pause()
            pauseMedia()
            palyPause.setImageResource(com.example.mediaplayer.R.drawable.play)

        } else if (actionString.equals(ACTION_NEXT, ignoreCase = true)) {
            //transportControls!!.skipToNext()
            skipToNext()
            palyPause.setImageResource(com.example.mediaplayer.R.drawable.pause)
        } else if (actionString.equals(ACTION_PREVIOUS, ignoreCase = true)) {
            //transportControls!!.skipToPrevious()
            skipToPrevious()
            palyPause.setImageResource(com.example.mediaplayer.R.drawable.pause)
        } else if (actionString.equals(ACTION_STOP, ignoreCase = true)) {
            transportControls!!.stop()
            stopMedia()
            palyPause.setImageResource(com.example.mediaplayer.R.drawable.play)
        }
    }


}

