package com.example.mediaplayer

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mediaplayer.AudioDetailsActivity.Companion.player
import com.example.mediaplayer.AudioDetailsActivity.Companion.serviceBound
import com.example.mediaplayer.AudioDetailsActivity.Companion.serviceConnection
import com.example.mediaplayer.Util.Companion.CHANNEL_ID


class MainActivity : AppCompatActivity() {

    lateinit var audioAdapter: AudioAdapter
    lateinit var audioList: ArrayList<Audio>

    companion object {
        var sentCheck:Boolean=false
        lateinit var recyclerView: RecyclerView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        Log.i("ok", "createeeeeeeeeeeeeeeeee")
//        if (getIntent().getStringExtra("service") != null)
//            serviceBound = getIntent().getStringExtra("service") as Boolean
        if (ContextCompat.checkSelfPermission(
                this,
                READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, READ_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE), 1)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(READ_EXTERNAL_STORAGE), 1)
            }
        } else {
            loadAudio()
            init()
        }

    }

    private fun init() {
        recyclerView = findViewById(R.id.recyclerview)
        audioAdapter = audioList?.let { AudioAdapter(it, this) }!!
        recyclerView.apply {
            layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(true)
            adapter = audioAdapter
            addOnItemTouchListener(
                CustomTouchListener(this@MainActivity, object : AudioAdapter.onItemClickListener {
                        override fun onClick(view: View?, index: Int) {
                            val bundle = Bundle()
                            bundle.putSerializable("audio", audioList.get(index))
                            val intent = Intent(this@MainActivity, AudioDetailsActivity::class.java)
                            intent.putExtras(bundle)
                            Log.i("ok", "here11111111111111111111")
                            sentCheck=true
                            startActivity(intent)
                        }
                    })
            )
        }
    }

    private fun loadAudio() {
        val contentResolver = contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor: Cursor? = contentResolver.query(uri, null, selection, null, sortOrder)
        if (cursor != null && cursor.getCount() > 0) {
            audioList = arrayListOf()
            while (cursor.moveToNext()) {
                val data: String =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val title: String =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val album: String =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artist: String =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                // Save to audioList
                audioList.add(Audio(data, title, album, artist, audioList.size))
            }
        }
        cursor?.close()
        val storage = AudioStorage(applicationContext)
        storage.storeAudioList(audioList)

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.size > 0 && grantResults.get(0) == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    loadAudio()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, "my notify", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            channel.description = "music"
            Toast.makeText(applicationContext, "heree", Toast.LENGTH_LONG)
        }
    }


    override fun onResume() {
        super.onResume()
        Log.i("ok", "resume")
    }

    override fun onPause() {
        super.onPause()
        Log.i("ok", "pause")
    }

    override fun onStop() {
        super.onStop()
        Log.i("ok", "stop")
    }

    override fun onDestroy() {
        super.onDestroy()
        //if(serviceBound)
        stopService(Intent(this@MainActivity, MediaPlayerService::class.java))

       // player?.stopSelf()
        Log.i("ok", "destroy11111111111111111111")
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
        //  super.onBackPressed()
      //  finish()
    }
}
