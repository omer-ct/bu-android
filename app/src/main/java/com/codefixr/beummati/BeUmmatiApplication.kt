package com.codefixr.beummati

import android.app.Application
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.LibraryProgressStore
import com.codefixr.beummati.data.PrayerNotifications
import com.codefixr.beummati.data.PrayerService
import com.codefixr.beummati.data.SalahTracker
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.player.LecturePlayerSession

class BeUmmatiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Catalogs.init(this)
        SettingsStore.init(this)
        SavedStore.init(this)
        SalahTracker.init(this)
        LibraryProgressStore.init(this)
        PrayerService.init(this)
        PrayerNotifications.init(this)
        LecturePlayerSession.init(this)
        // Re-arm from the cached day so notifications survive a cold start with no network.
        PrayerNotifications.reschedule(this)
    }
}
