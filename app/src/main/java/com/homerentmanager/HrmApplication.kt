package com.homerentmanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class HrmApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        container.scheduleBackgroundWork()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                BACKUP_CHANNEL_ID,
                "Backup reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminds you to create an encrypted manual backup"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val BACKUP_CHANNEL_ID = "backup_reminders"
    }
}
