package com.comsat.audio.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.HandlerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.comsat.audio.ComsatApp
import com.comsat.audio.MainActivity
import com.comsat.audio.R
import com.comsat.audio.data.model.StreamState
import com.comsat.audio.data.model.StreamStatus
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudioService : Service() {

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    @Inject lateinit var okHttpClient: OkHttpClient

    private val binder = AudioBinder()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var atcPlayer: ExoPlayer
    private lateinit var somaPlayer: ExoPlayer

    private val _atcState = MutableStateFlow(StreamState())
    val atcState: StateFlow<StreamState> = _atcState

    private val _somaState = MutableStateFlow(StreamState())
    val somaState: StateFlow<StreamState> = _somaState

    // ICY metadata: current track title on the Soma stream
    private val _somaNowPlaying = MutableStateFlow<String?>(null)
    val somaNowPlaying: StateFlow<String?> = _somaNowPlaying

    // Live FFT bands per stream (see SpectrumProcessor), tapped pre-fader
    private val atcSpectrumProcessor = SpectrumProcessor()
    private val somaSpectrumProcessor = SpectrumProcessor()
    val atcSpectrum: StateFlow<FloatArray> get() = atcSpectrumProcessor.bands
    val somaSpectrum: StateFlow<FloatArray> get() = somaSpectrumProcessor.bands

    private var atcUrl: String? = null
    private var somaUrl: String? = null

    private var atcLabel: String? = null
    private var somaLabel: String? = null

    // Track-chained station (Rain Radio): non-null while the soma slot plays a
    // station that has no continuous stream. Each mp3 is fetched via the
    // station's /next-track API and the next one is chained on STATE_ENDED.
    private var somaNextTrackApiBase: String? = null
    private var somaCurrentTrackName: String? = null
    private var somaNextTrackCall: Call? = null

    private var atcRetryAttempt = 0
    private var somaRetryAttempt = 0

    // Tokens to cancel pending reconnects per stream
    private val atcReconnectToken = Any()
    private val somaReconnectToken = Any()

    // ─── Audio focus ──────────────────────────────────────────────────────────

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var ducked = false
    private var resumeOnFocusGain = false

    private var baseAtcVolume = 1f
    private var baseSomaVolume = 1f

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pauseAll()
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = _atcState.value.status in PAUSABLE_STATUSES ||
                    _somaState.value.status in PAUSABLE_STATUSES
                pauseAll()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                applyVolumes()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                ducked = false
                applyVolumes()
                if (resumeOnFocusGain) resumeAll()
                resumeOnFocusGain = false
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Headphones unplugged: don't blast ATC through the speaker
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseAll()
        }
    }

    // ─── MediaSession (composite of both streams) ─────────────────────────────

    private lateinit var compositePlayer: CompositePlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        atcPlayer = buildPlayer(isAtc = true)
        somaPlayer = buildPlayer(isAtc = false)
        compositePlayer = CompositePlayer()
        mediaSession = MediaSession.Builder(this, compositePlayer)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        ContextCompat.registerReceiver(
            this, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // There is no durable stream descriptor to restore after process death.
        // Restarting an empty sticky service would leave a permanent idle
        // notification, so only explicit starts are accepted.
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(ComsatApp.NOTIFICATION_ID, buildNotification())
        when (intent.action) {
            ACTION_TOGGLE_ALL -> toggleAll()
            ACTION_STOP_ALL -> stopAll()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        somaNextTrackCall?.cancel()
        abandonFocus()
        mediaSession.release()
        compositePlayer.release()
        atcPlayer.release()
        somaPlayer.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestFocus(): Boolean {
        if (focusRequest != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        val granted = audioManager.requestAudioFocus(request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) focusRequest = request
        return granted
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        ducked = false
    }

    private fun applyVolumes() {
        val factor = if (ducked) DUCK_FACTOR else 1f
        atcPlayer.volume = baseAtcVolume * factor
        somaPlayer.volume = baseSomaVolume * factor
    }

    // ─── Public controls ──────────────────────────────────────────────────────

    fun playAtc(url: String, label: String? = null) {
        atcUrl = url
        atcLabel = label
        atcRetryAttempt = 0
        handler.removeCallbacksAndMessages(atcReconnectToken)
        requestFocus()
        _atcState.value = StreamState(StreamStatus.LOADING)
        atcPlayer.setMediaItem(MediaItem.fromUri(url))
        atcPlayer.prepare()
        atcPlayer.play()
        ensureForeground()
        updateNotification()
    }

    fun stopAtc() {
        atcUrl = null
        atcLabel = null
        atcRetryAttempt = 0
        handler.removeCallbacksAndMessages(atcReconnectToken)
        atcPlayer.stop()
        atcSpectrumProcessor.reset()
        _atcState.value = StreamState(StreamStatus.IDLE)
        maybeStopForeground()
        updateNotification()
    }

    fun playSoma(url: String, label: String? = null) {
        clearChainedPlayback()
        somaUrl = url
        somaLabel = label
        somaRetryAttempt = 0
        handler.removeCallbacksAndMessages(somaReconnectToken)
        requestFocus()
        _somaNowPlaying.value = null
        _somaState.value = StreamState(StreamStatus.LOADING)
        somaPlayer.setMediaItem(MediaItem.fromUri(url))
        somaPlayer.prepare()
        somaPlayer.play()
        ensureForeground()
        updateNotification()
    }

    // Track-chained station: no continuous stream URL; resolve the first track
    // via /next-track and keep chaining from the player's STATE_ENDED.
    fun playSomaChained(apiBase: String, label: String? = null) {
        clearChainedPlayback()
        // Do not let the previously selected SomaFM stream continue underneath
        // while the first chained track is being resolved.
        somaPlayer.stop()
        somaSpectrumProcessor.reset()
        somaNextTrackApiBase = apiBase.trimEnd('/')
        somaUrl = null
        somaLabel = label
        somaRetryAttempt = 0
        handler.removeCallbacksAndMessages(somaReconnectToken)
        requestFocus()
        _somaNowPlaying.value = null
        _somaState.value = StreamState(StreamStatus.LOADING)
        ensureForeground()
        updateNotification()
        fetchNextChainedTrack()
    }

    fun stopSoma() {
        clearChainedPlayback()
        somaUrl = null
        somaLabel = null
        somaRetryAttempt = 0
        handler.removeCallbacksAndMessages(somaReconnectToken)
        somaPlayer.stop()
        somaSpectrumProcessor.reset()
        _somaNowPlaying.value = null
        _somaState.value = StreamState(StreamStatus.IDLE)
        maybeStopForeground()
        updateNotification()
    }

    private fun clearChainedPlayback() {
        somaNextTrackApiBase = null
        somaCurrentTrackName = null
        somaNextTrackCall?.cancel()
        somaNextTrackCall = null
    }

    private fun fetchNextChainedTrack() {
        val apiBase = somaNextTrackApiBase ?: return
        // null distinguishes "waiting for the next file" from a paused file.
        // resumeAll() can then restart the API request instead of replaying an
        // already-ended track.
        somaUrl = null
        somaNextTrackCall?.cancel()
        val requestUrl = buildString {
            append(apiBase).append("/next-track")
            somaCurrentTrackName?.let { append("?current=").append(Uri.encode(it)) }
        }
        val call = okHttpClient.newCall(Request.Builder().url(requestUrl).build())
        somaNextTrackCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                handler.post {
                    if (somaNextTrackCall !== call) return@post
                    somaNextTrackCall = null
                    if (somaNextTrackApiBase == apiBase &&
                        _somaState.value.status == StreamStatus.LOADING
                    ) {
                        onChainedFetchFailed()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val track = response.use { resp ->
                    if (!resp.isSuccessful) null
                    else runCatching {
                        val json = JSONObject(resp.body.string())
                        val path = json.getString("url")
                        path to json.optString("name", path.substringAfterLast('/'))
                    }.getOrNull()
                }
                handler.post {
                    if (somaNextTrackCall !== call) return@post
                    somaNextTrackCall = null
                    // Station may have been stopped, paused or retuned while
                    // the HTTP callback was in flight.
                    if (somaNextTrackApiBase != apiBase ||
                        _somaState.value.status != StreamStatus.LOADING
                    ) return@post
                    val trackUrl = track?.let { resolveChainedTrackUrl(apiBase, it.first) }
                    if (track == null || trackUrl == null) onChainedFetchFailed()
                    else playChainedTrack(trackUrl, track.second)
                }
            }
        })
    }

    private fun playChainedTrack(url: String, name: String) {
        somaUrl = url
        somaCurrentTrackName = name
        // Same cleanup the station's own web player applies to file names
        _somaNowPlaying.value = name
            .removeSuffix(".mp3")
            .replace(Regex("[-_]+"), " ")
            .trim()
            .ifBlank { null }
        _somaState.value = StreamState(StreamStatus.LOADING)
        somaPlayer.setMediaItem(MediaItem.fromUri(url))
        somaPlayer.prepare()
        somaPlayer.play()
        updateNotification()
    }

    private fun onChainedFetchFailed() {
        if (somaNextTrackApiBase == null) return
        val attempt = ++somaRetryAttempt
        _somaState.value = StreamState(StreamStatus.RECONNECTING, "STREAM OFFLINE")
        val delayMs = when (attempt) {
            1 -> 5_000L
            2 -> 10_000L
            else -> 30_000L
        }
        HandlerCompat.postDelayed(handler, {
            if (_somaState.value.status == StreamStatus.RECONNECTING) {
                _somaState.value = StreamState(StreamStatus.LOADING)
                fetchNextChainedTrack()
            }
        }, somaReconnectToken, delayMs)
        updateNotification()
    }

    fun setAtcVolume(v: Float) {
        baseAtcVolume = v.coerceIn(0f, 1f)
        applyVolumes()
    }

    fun setSomaVolume(v: Float) {
        baseSomaVolume = v.coerceIn(0f, 1f)
        applyVolumes()
    }

    // ─── Master controls (notification) ───────────────────────────────────────

    private fun toggleAll() {
        val anyActive = _atcState.value.status in PAUSABLE_STATUSES ||
            _somaState.value.status in PAUSABLE_STATUSES
        if (anyActive) pauseAll() else resumeAll()
    }

    private fun pauseAll() {
        if (_atcState.value.status in PAUSABLE_STATUSES) {
            handler.removeCallbacksAndMessages(atcReconnectToken)
            atcPlayer.pause()
            _atcState.value = StreamState(StreamStatus.PAUSED)
        }
        if (_somaState.value.status in PAUSABLE_STATUSES) {
            handler.removeCallbacksAndMessages(somaReconnectToken)
            // Cancelling alone is not enough: an already-posted response is
            // also gated by the PAUSED state in fetchNextChainedTrack().
            if (somaNextTrackApiBase != null &&
                _somaState.value.status == StreamStatus.LOADING
            ) {
                somaNextTrackCall?.cancel()
                somaNextTrackCall = null
            }
            somaPlayer.pause()
            _somaState.value = StreamState(StreamStatus.PAUSED)
        }
        updateNotification()
    }

    private fun resumeAll() {
        if (_atcState.value.status == StreamStatus.PAUSED ||
            _somaState.value.status == StreamStatus.PAUSED
        ) requestFocus()
        resumeStream(atcPlayer, atcUrl, _atcState, rejoinLiveEdge = true)
        if (somaNextTrackApiBase != null && somaUrl == null &&
            _somaState.value.status == StreamStatus.PAUSED
        ) {
            // Chained station paused before its first track resolved
            _somaState.value = StreamState(StreamStatus.LOADING)
            fetchNextChainedTrack()
        } else {
            resumeStream(
                somaPlayer, somaUrl, _somaState,
                // A chained track is a plain file, not a live stream — resume
                // where it paused instead of restarting from the beginning.
                rejoinLiveEdge = somaNextTrackApiBase == null
            )
        }
        updateNotification()
    }

    private fun resumeStream(
        player: ExoPlayer,
        url: String?,
        stateFlow: MutableStateFlow<StreamState>,
        rejoinLiveEdge: Boolean
    ) {
        if (url == null || stateFlow.value.status != StreamStatus.PAUSED) return
        stateFlow.value = StreamState(StreamStatus.LOADING)
        if (player.playbackState == Player.STATE_IDLE) {
            // Was paused mid-reconnect; needs a fresh prepare
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        } else if (rejoinLiveEdge) {
            // Rejoin the live edge instead of replaying stale buffer
            player.seekToDefaultPosition()
        }
        player.play()
        if (player.playbackState == Player.STATE_READY) {
            stateFlow.value = StreamState(StreamStatus.PLAYING)
        }
    }

    private fun stopAll() {
        stopAtc()
        stopSoma()
    }

    // ─── Player factory ───────────────────────────────────────────────────────

    private fun buildPlayer(isAtc: Boolean): ExoPlayer {
        // OkHttpDataSource follows redirects (including cross-protocol ones) and
        // shares the app's OkHttpClient. d.liveatc.net 302-redirects every stream
        // request to the active Icecast server.
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        // TeeAudioProcessor mirrors the decoded PCM into the spectrum analyzer
        // without touching the audio path (and without RECORD_AUDIO).
        val spectrumProcessor = if (isAtc) atcSpectrumProcessor else somaSpectrumProcessor
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(spectrumProcessor)))
                .build()
        }
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // handleAudioFocus=false: two players would steal focus from each other.
            // Focus is managed once at the service level instead.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false
            )
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val stateFlow = if (isAtc) _atcState else _somaState
                // Chained station: a finished track is not the end of the
                // broadcast — fetch the next one and keep going.
                if (!isAtc && state == Player.STATE_ENDED && somaNextTrackApiBase != null) {
                    somaUrl = null
                    stateFlow.value = StreamState(StreamStatus.LOADING)
                    fetchNextChainedTrack()
                    updateNotification()
                    return
                }
                stateFlow.value = when (state) {
                    Player.STATE_BUFFERING ->
                        if (stateFlow.value.status == StreamStatus.PAUSED) stateFlow.value
                        else StreamState(StreamStatus.BUFFERING)
                    Player.STATE_READY ->
                        if (player.playWhenReady) {
                            if (isAtc) atcRetryAttempt = 0 else somaRetryAttempt = 0
                            StreamState(StreamStatus.PLAYING)
                        } else stateFlow.value
                    // On error ExoPlayer transitions to STATE_IDLE; keep RECONNECTING
                    // so the scheduled retry isn't silently disarmed.
                    Player.STATE_IDLE ->
                        if (player.playerError != null ||
                            stateFlow.value.status == StreamStatus.RECONNECTING ||
                            stateFlow.value.status == StreamStatus.PAUSED
                        ) stateFlow.value
                        else StreamState(StreamStatus.IDLE)
                    Player.STATE_ENDED -> StreamState(StreamStatus.IDLE)
                    else -> stateFlow.value
                }
                updateNotification()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (!isAtc) {
                    val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                    // Chained tracks rarely carry tags; keep the /next-track
                    // name rather than clearing it with a null ID3 title.
                    if (somaNextTrackApiBase == null || title != null) {
                        _somaNowPlaying.value = title
                    }
                    updateNotification()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val stateFlow = if (isAtc) _atcState else _somaState
                val url = if (isAtc) atcUrl else somaUrl
                val attempt = if (isAtc) ++atcRetryAttempt else ++somaRetryAttempt
                stateFlow.value = StreamState(StreamStatus.RECONNECTING, friendlyErrorMessage(error))
                // Back-off reconnect: 5 s, then 10 s, then 30 s
                scheduleReconnect(player, url, stateFlow, attempt, isAtc)
            }
        })
        return player
    }

    // Raw PlaybackException messages leak stack-level details ("Unable to resolve
    // host…"); collapse them into short statuses matching the app's HUD style.
    private fun friendlyErrorMessage(error: PlaybackException): String =
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "NO SIGNAL · CHECK CONNECTION"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                "STREAM OFFLINE"
            else -> "PLAYBACK FAULT"
        }

    private fun scheduleReconnect(
        player: ExoPlayer,
        url: String?,
        stateFlow: MutableStateFlow<StreamState>,
        attempt: Int,
        isAtc: Boolean
    ) {
        if (url == null) return
        val delayMs = when (attempt) {
            1 -> 5_000L
            2 -> 10_000L
            else -> 30_000L
        }
        val token = if (isAtc) atcReconnectToken else somaReconnectToken
        HandlerCompat.postDelayed(handler, {
            if (stateFlow.value.status == StreamStatus.RECONNECTING) {
                stateFlow.value = StreamState(StreamStatus.LOADING)
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
            }
        }, token, delayMs)
    }

    // ─── Foreground service ───────────────────────────────────────────────────

    private fun ensureForeground() {
        startService(Intent(this, AudioService::class.java))
        startForeground(ComsatApp.NOTIFICATION_ID, buildNotification())
    }

    private fun maybeStopForeground() {
        if (_atcState.value.status == StreamStatus.IDLE &&
            _somaState.value.status == StreamStatus.IDLE
        ) {
            abandonFocus()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateNotification() {
        // Posted so it never runs re-entrantly inside a CompositePlayer handle* call
        handler.post { compositePlayer.refresh() }
        // Don't resurrect a notification the service is about to drop
        if (_atcState.value.status == StreamStatus.IDLE &&
            _somaState.value.status == StreamStatus.IDLE
        ) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this)
                .notify(ComsatApp.NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val togglePending = servicePendingIntent(ACTION_TOGGLE_ALL, requestCode = 1)
        val stopPending = servicePendingIntent(ACTION_STOP_ALL, requestCode = 2)

        val anyActive = _atcState.value.status in PAUSABLE_STATUSES ||
            _somaState.value.status in PAUSABLE_STATUSES
        val activeLabels = listOfNotNull(
            atcLabel.takeIf { _atcState.value.status != StreamStatus.IDLE },
            somaLabel.takeIf { _somaState.value.status != StreamStatus.IDLE }
        )
        val contentText = when {
            activeLabels.isEmpty() -> getString(R.string.notification_text)
            anyActive -> activeLabels.joinToString(" + ")
            else -> "${getString(R.string.notification_paused)} · ${activeLabels.joinToString(" + ")}"
        }

        return NotificationCompat.Builder(this, ComsatApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                if (anyActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                getString(if (anyActive) R.string.notification_action_pause else R.string.notification_action_play),
                togglePending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                stopPending
            )
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, AudioService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    // ─── Composite player facade ──────────────────────────────────────────────
    //
    // MediaSession drives exactly one Player, but this service runs two ExoPlayers.
    // This facade presents their combined state as a single live "COMSAT mix" item
    // and maps transport commands onto the master pause/resume/stop controls, so
    // headset buttons, lock screen and system media controls all work.
    private inner class CompositePlayer : SimpleBasePlayer(Looper.getMainLooper()) {

        fun refresh() = invalidateState()

        override fun getState(): State {
            val statuses = listOf(_atcState.value.status, _somaState.value.status)
            val anyActive = statuses.any { it in PAUSABLE_STATUSES }
            val anyBuffering = statuses.any {
                it == StreamStatus.LOADING || it == StreamStatus.BUFFERING ||
                    it == StreamStatus.RECONNECTING
            }
            val anyPaused = statuses.any { it == StreamStatus.PAUSED }

            val playbackState = when {
                anyBuffering -> STATE_BUFFERING
                anyActive || anyPaused -> STATE_READY
                else -> STATE_IDLE
            }

            val title = listOfNotNull(
                atcLabel.takeIf { _atcState.value.status != StreamStatus.IDLE },
                (_somaNowPlaying.value ?: somaLabel)
                    .takeIf { _somaState.value.status != StreamStatus.IDLE }
            ).joinToString(" + ").ifEmpty { getString(R.string.notification_title) }

            val mediaItem = MediaItemData.Builder(/* uid = */ "comsat-mix")
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId("comsat-mix")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(getString(R.string.app_name))
                                .build()
                        )
                        .build()
                )
                .setIsDynamic(true)
                .setDurationUs(C.TIME_UNSET)
                .build()

            return State.Builder()
                .setAvailableCommands(
                    Player.Commands.Builder()
                        .addAll(
                            COMMAND_PLAY_PAUSE,
                            COMMAND_PREPARE,
                            COMMAND_STOP,
                            COMMAND_GET_CURRENT_MEDIA_ITEM,
                            COMMAND_GET_TIMELINE,
                            COMMAND_GET_METADATA
                        )
                        .build()
                )
                .setPlaylist(listOf(mediaItem))
                .setCurrentMediaItemIndex(0)
                .setPlaybackState(playbackState)
                .setPlayWhenReady(anyActive, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (playWhenReady) resumeAll() else pauseAll()
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> {
            resumeAll()
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            stopAll()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }

    companion object {
        const val ACTION_TOGGLE_ALL = "com.comsat.audio.action.TOGGLE_ALL"
        const val ACTION_STOP_ALL = "com.comsat.audio.action.STOP_ALL"

        private const val DUCK_FACTOR = 0.3f

        private val PAUSABLE_STATUSES = setOf(
            StreamStatus.PLAYING,
            StreamStatus.BUFFERING,
            StreamStatus.LOADING,
            StreamStatus.RECONNECTING
        )
    }
}
