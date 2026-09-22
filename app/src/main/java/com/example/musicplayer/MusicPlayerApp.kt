package com.example.musicplayer

import android.app.Application
import com.example.musicplayer.data.db.AppDatabase

class MusicPlayerApp : Application() {
    val database by lazy { AppDatabase.get(this) }
}
