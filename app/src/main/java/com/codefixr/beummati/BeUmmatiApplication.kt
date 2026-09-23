package com.codefixr.beummati

import android.app.Application
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.player.LecturePlayerSession

class BeUmmatiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Catalogs.init(this)
        SavedStore.init(this)
        LecturePlayerSession.init(this)
    }
}
