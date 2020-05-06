package com.example.mediaplayer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.widget.Toast

class Util  {


    companion object {
        const val Broadcast_PLAY_NEW_AUDIO = "PlayNewAudio"
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_PREVIOUS = "ACTION_PREVIOUS"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "c1"
    }
}