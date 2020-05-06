package com.example.mediaplayer

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type


class AudioStorage(var context: Context) {

    private lateinit var sharedPreferences:SharedPreferences
    private val STORAGE:String="STORAGE"

    public fun storeAudioList(data: ArrayList<Audio>){
        sharedPreferences=context.getSharedPreferences(STORAGE,MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        val gson= Gson()
        val json=gson.toJson(data)
        editor.putString("dataList",json)
        editor.apply()
    }

    public fun storeAudio(data: Audio){
        sharedPreferences=context.getSharedPreferences(STORAGE,MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        val gson= Gson()
        val json=gson.toJson(data)
        editor.putString("data",json)
        editor.apply()
    }

    public fun loadAudioList():ArrayList<Audio>{
        sharedPreferences=context.getSharedPreferences(STORAGE,MODE_PRIVATE)
        val gson= Gson()
        val json=sharedPreferences.getString("dataList",null)
        val type: Type = object : TypeToken<ArrayList<Audio?>?>() {}.type
        return gson.fromJson(json , type)
    }

    public fun loadAudio():Audio{
        sharedPreferences=context.getSharedPreferences(STORAGE,MODE_PRIVATE)
        val gson= Gson()
        val json=sharedPreferences.getString("data",null)
        val type: Type = object : TypeToken<Audio?>() {}.type
        return gson.fromJson(json , type)
    }

    public fun storeAudioIndex(index:Int){
        sharedPreferences=context.getSharedPreferences(STORAGE,MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putInt("index",index)
        editor.apply()
    }

    public fun loadAudioIndex():Int{
        sharedPreferences=context.getSharedPreferences(STORAGE,MODE_PRIVATE)
        return sharedPreferences.getInt("index",-1)
    }

    public fun clearData(){
        sharedPreferences=context.getSharedPreferences(STORAGE,MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }
}