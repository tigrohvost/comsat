package com.comsat.audio.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.comsat.audio.data.repository.AirportCatalog
import com.comsat.audio.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val playbackRepository: PlaybackSnapshotRepository,
    private val airportCatalog: AirportCatalog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()
    private val manager = AppWidgetManager.getInstance(context)
    private val providers = listOf(
        ComsatWidgetProvider::class.java to WidgetFormat.FULL,
        ComsatSquareWidgetProvider::class.java to WidgetFormat.SQUARE,
        ComsatSlimWidgetProvider::class.java to WidgetFormat.SLIM
    ).map { (type, format) -> ComponentName(context, type) to format }

    private fun installedWidgets() = providers.flatMap { (provider, format) ->
        manager.getAppWidgetIds(provider).map { id -> id to format }
    }
    private var observation: Job? = null

    @Synchronized
    fun start() {
        if (observation?.isActive == true) return
        observation = scope.launch {
            var retryDelay = 1_000L
            while (isActive) {
                try {
                    // Widget IPC is optional app functionality. Keep this
                    // lookup off Application.onCreate's unguarded call path.
                    // Rechecking on retries also ends recovery if no widgets remain.
                    if (installedWidgets().isEmpty()) return@launch
                    combine(settingsRepository.settings, playbackRepository.snapshot) { settings, playback ->
                        widgetModel(settings, playback, settings.airportIcao?.let(airportCatalog::find))
                    }.distinctUntilChanged().collect {
                        withTimeout(8_000) { refresh() }
                        retryDelay = 1_000L
                    }
                    return@launch
                } catch (error: TimeoutCancellationException) {
                    Log.w(TAG, "Timed out observing widget state; retrying", error)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "Could not observe widget state; retrying", error)
                }
                // Only failures schedule retries; successful observation is
                // event-driven. onDisabled/stop cancels this delay immediately.
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(60_000L)
            }
        }
    }

    @Synchronized
    fun stop() {
        observation?.cancel()
        observation = null
    }

    // Also used by goAsync receivers: bounded local work, without fetching
    // stations, probing streams, or starting/binding the playback service.
    fun refreshAsync(): Job = scope.launch { refreshSafely() }

    private suspend fun refreshSafely() {
        try {
            withTimeout(8_000) { refresh() }
        } catch (error: TimeoutCancellationException) {
            Log.w(TAG, "Timed out updating widget", error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not update widget", error)
        }
    }

    private suspend fun refresh() = renderMutex.withLock {
        val widgets = installedWidgets()
        if (widgets.isEmpty()) {
            stop()
            return@withLock
        }
        // Read inside the lock instead of rendering the collector's possibly
        // older value after a provider resize/update has already refreshed it.
        val settings = settingsRepository.settings.first()
        val model = widgetModel(
            settings,
            playbackRepository.snapshot.value,
            settings.airportIcao?.let(airportCatalog::find)
        )
        widgets.forEach { (id, format) ->
            manager.updateAppWidget(id, WidgetViews.render(context, model, manager.getAppWidgetOptions(id), format))
        }
    }

    private companion object {
        const val TAG = "ComsatWidget"
    }
}
