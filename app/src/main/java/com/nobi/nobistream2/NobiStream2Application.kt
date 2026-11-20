package com.nobi.nobistream2

import android.app.Application
import com.nobi.nobistream2.data.ServiceLocator
import com.nobi.nobistream2.ui.player.PlayerSession

/**
 * Application entry point. Holds the single [ServiceLocator] used by all
 * screens, and the single [PlayerSession] so playback (and the
 * mini-player) survive navigation between screens. Do not add business
 * logic here.
 */
class NobiStream2Application : Application() {

    lateinit var serviceLocator: ServiceLocator
        private set

    val playerSession: PlayerSession by lazy { PlayerSession(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        serviceLocator = ServiceLocator(this)
    }

    companion object {
        lateinit var instance: NobiStream2Application
            private set
    }
}
