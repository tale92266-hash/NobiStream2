package com.nobi.nobistream2.data

import android.content.Context
import androidx.room.Room
import com.nobi.nobistream2.data.db.AppDatabase

/**
 * Single place that builds the app's dependencies. Created once by
 * [com.nobi.nobistream2.NobiStream2Application] and shared by all screens.
 */
class ServiceLocator(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "nobistream2.db"
    ).fallbackToDestructiveMigration().build()

    val settingsStore = SettingsStore(appContext)

    val credentialsStore = ApiCredentialsStore(appContext)

    val gateway: TelegramGateway = TdLibGateway(appContext)

    val repository = NobiRepository(
        appContext,
        gateway,
        database,
        settingsStore,
        credentialsStore
    )
}
