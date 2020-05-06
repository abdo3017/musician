package com.example.mediaplayer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mediaplayer.MainActivity.Companion.sentCheck
import com.example.mediaplayer.Util.Companion.Broadcast_PLAY_NEW_AUDIO
import kotlinx.android.synthetic.main.activity_audio_details.*

class AudioDetailsActivity : AppCompatActivity(), View.OnClickListener {


    companion object {
        var checkRepeat: Int = 0
        lateinit var ctime: TextView
        lateinit var name: TextView
        lateinit var totalTime: TextView
        lateinit var seekbar: SeekBar
        lateinit var palyPause: ImageButton
        var player: MediaPlayerService? = null
        var serviceBound = false
        val serviceConnection: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.i("okkkkkk", "connected")
                // We've bound to LocalService, cast the IBinder and get LocalService instance
                val binder: MediaPlayerService.MediaPlayerBinder = service as MediaPlayerService.MediaPlayerBinder
                player = binder.getService()
                serviceBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceBound = false
                Log.i("okkkkkk", "disconnected")
            }
        }

    }


    lateinit var next: ImageButton
    lateinit var repating: ImageButton

    lateinit var back: ImageButton
    lateinit var audio: Audio
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_details)
        Log.i("okkkkkk", "createeeeeeeeeeeeeeeeee")
        val storage = AudioStorage(applicationContext)
        if(sentCheck){
            sentCheck=false
            if (getIntent().getSerializableExtra("audio") != null) {
                audio = getIntent().getSerializableExtra("audio") as Audio
                storage.storeAudio(audio)
            } else audio = storage.loadAudio()
        } else audio = storage.loadAudio()
        init()
        playAudio()
    }

    private fun init() {
        repating = findViewById(R.id.repeat)
        seekbar = findViewById(R.id.seekBar)
        ctime = findViewById(R.id.curtime)
        next = findViewById(R.id.next)
        totalTime = findViewById(R.id.totaltime)
        name = findViewById(R.id.title)
        back = findViewById(R.id.prevuos)
        palyPause = findViewById(R.id.playpause)
        name.text = audio.title
        playpause.setOnClickListener(this)
        next.setOnClickListener(this)
        back.setOnClickListener(this)
        repating.setOnClickListener(this)

        val seek: SeekBar.OnSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser)
                    player?.mediaPlayer?.seekTo(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        }
        seekBar.setOnSeekBarChangeListener(seek)
    }

    fun playAudio() {
        val storage = AudioStorage(applicationContext)
        storage.storeAudioIndex(audio.index)
        //Check is service is active
            val playerIntent = Intent(this, MediaPlayerService::class.java)
        if (!serviceBound) {
            Log.i("okkkkkk", "started")

            val bundle = Bundle()
            bundle.putSerializable("audio", audio)
            playerIntent.putExtras(bundle)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            serviceBound=true
        } else {
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            val broadcastIntent = Intent(Broadcast_PLAY_NEW_AUDIO)
            sendBroadcast(broadcastIntent)
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean("ServiceState", serviceBound)
        savedInstanceState.putSerializable("audio", audio)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState.getBoolean("ServiceState")
        audio = savedInstanceState.getSerializable("audio") as Audio
    }

    override fun onResume() {
        super.onResume()

        Log.i("okkkkkk", "resume")
    }

    override fun onPause() {
        super.onPause()
        Log.i("okkkkkkkkkk", "pause")
    }

    override fun onStop() {
        super.onStop()
        Log.i("okkkkkkk", "stop")
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        Log.i("ok", "destroy")
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.next -> {
                player?.skipToNext()
            }
            R.id.prevuos -> {
                Toast.makeText(this@AudioDetailsActivity, "hwrw", Toast.LENGTH_SHORT).show()
                player?.skipToPrevious()
            }
            R.id.playpause -> {
                if (player?.mediaPlayer?.isPlaying!!) {
                    player?.pauseMedia()
                    playpause.setImageResource(R.drawable.play)
                } else {
                    playpause.setImageResource(R.drawable.pause)
                    player?.resumeMedia()
                }
            }
            R.id.repeat -> {
                checkRepeat++
                checkRepeat%=3
                if (checkRepeat==1) {
                    repating.setImageResource(R.drawable.repate1)
                } else if (checkRepeat==2) {
                    repating.setImageResource(R.drawable.repeat)
                }
                else{
                    repating.setImageResource(R.drawable.norepeat)
                }

            }
        }
    }

}
