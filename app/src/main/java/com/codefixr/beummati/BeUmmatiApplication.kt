package com.codefixr.beummati

import android.app.Application
import com.codefixr.beummati.data.Catalogs
import com.codefixr.beummati.data.HifzNotifications
import com.codefixr.beummati.data.HifzStore
import com.codefixr.beummati.data.LibraryProgressStore
import com.codefixr.beummati.data.OfflinePacks
import com.codefixr.beummati.data.PrayerNotifications
import com.codefixr.beummati.data.PrayerService
import com.codefixr.beummati.data.QuranAudioCache
import com.codefixr.beummati.data.QuranTts
import com.codefixr.beummati.data.ReadingPlanStore
import com.codefixr.beummati.data.SalahTracker
import com.codefixr.beummati.data.SavedStore
import com.codefixr.beummati.data.SettingsStore
import com.codefixr.beummati.data.WiridStore
import com.codefixr.beummati.player.LecturePlayerSession
import com.codefixr.beummati.player.QuranAyahPlayer

class BeUmmatiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Catalogs.init(this)
        SettingsStore.init(this)
        OfflinePacks.init(this)
        SavedStore.init(this)
        SalahTracker.init(this)
        HifzStore.init(this)
        QuranAudioCache.init(this)
        QuranAyahPlayer.init(this)
        QuranTts.init(this)
        WiridStore.init(this)
        ReadingPlanStore.init(this)
        LibraryProgressStore.init(this)
        PrayerService.init(this)
        PrayerNotifications.init(this)
        HifzNotifications.init(this)
        LecturePlayerSession.init(this)
        // Re-arm from the cached day so notifications survive a cold start with no network.
        PrayerNotifications.reschedule(this)
        HifzNotifications.reschedule(this)
    }
}
