package com.comsat.audio.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ComsatWidgetProvider : AppWidgetProvider() {
    @Inject lateinit var updater: WidgetUpdater

    // AppWidgetProvider dispatches restored IDs through onUpdate as well.
    // Taking goAsync in both callbacks would consume the same pending result.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        requestRefresh()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        requestRefresh()
    }

    override fun onDisabled(context: Context) {
        updater.stop()
    }

    private fun requestRefresh() {
        val pendingResult = goAsync()
        try {
            updater.start()
            updater.refreshAsync().invokeOnCompletion { pendingResult.finish() }
        } catch (error: Exception) {
            pendingResult.finish()
            Log.w("ComsatWidget", "Could not schedule widget update", error)
        }
    }
}
