package com.comsat.audio

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.comsat.audio.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ComsatApp : Application() {
    @Inject lateinit var widgetUpdater: WidgetUpdater

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        widgetUpdater.start()
    }

    private fun createNotificationChannel() {
        // minSdk is 26, where notification channels are always available.
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "COMSAT audio stream controls"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "comsat_audio"
        const val NOTIFICATION_ID = 1001
    }
}
