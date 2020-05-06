package com.example.mediaplayer

import java.io.Serializable

class Audio(data: String, title: String, album: String, artist: String,index:Int) : Serializable {
    public var data: String = data
    public var title: String = title
    public var album: String = album
    public var artist: String = artist
    public var index: Int = index
}