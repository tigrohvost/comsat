package com.comsat.audio.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.AlarmManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
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
import androidx.media3.datasource.HttpDataSource
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
import com.comsat.audio.data.repository.SettingsRepository
import com.comsat.audio.data.model.StreamState
import com.comsat.audio.data.model.StreamStatus
import com.comsat.audio.widget.PlaybackSnapshotRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.CacheControl
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var playbackSnapshotRepository: PlaybackSnapshotRepository

    private val widgetStateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val binder = AudioBinder()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var atcPlayer: ExoPlayer
    private lateinit var somaPlayer: ExoPlayer

    private val _atcState = MutableStateFlow(StreamState())
    val atcState: StateFlow<StreamState> = _atcState

    private val _somaState = MutableStateFlow(StreamState())
    val somaState: StateFlow<StreamState> = _somaState
    private val _stopGeneration = MutableStateFlow(0L)
    val stopGeneration: StateFlow<Long> = _stopGeneration

    // ICY metadata: current track title on the Soma stream
    private val _somaNowPlaying = MutableStateFlow<String?>(null)
    val somaNowPlaying: StateFlow<String?> = _somaNowPlaying

    // Live FFT bands per stream (see SpectrumProcessor), tapped pre-fader
    private val atcSpectrumProcessor = SpectrumProcessor(measureLevel = true)
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
    private var somaPendingSelection: ChainedTrackSelection? = null

    // Live link of a chained station (see StationLiveLink.kt): while its music plays,
    // the station is told somebody listens and its host's voice clips are played over
    // the ducked music. liveGeneration invalidates callbacks of a link that has ended.
    private val liveSession = java.util.UUID.randomUUID().toString()
    private val sayFeed = SayFeed()
    private val voiceQueue = ArrayDeque<SayClip>()
    private var liveBase: String? = null
    private var liveToken: String? = null
    private var liveGeneration = 0
    private var liveHeartbeatCall: Call? = null
    private var liveSayCall: Call? = null
    private var voicePlayer: ExoPlayer? = null
    // speaking: a line is in hand (loading or sounding). voiceSounding: it is audible;
    // only then is the music lowered and the line shown.
    private var speaking = false
    private var voiceSounding = false
    private var voiceGapUntil = 0L
    private val liveHeartbeatToken = Any()
    private val liveSayToken = Any()
    private val voiceGapToken = Any()
    private val voiceWatchToken = Any()

    // What the ambient line shows: the host's spoken line while it sounds, else the music title.
    private var somaMusicTitle: String? = null
    private var spokenLine: String? = null

    // A selection the station may already have answered survives a panel stop and start
    // of the same station, as it survives a pause: the piece it chose is not skipped.
    private var somaCarriedSelection: Pair<String, ChainedTrackSelection>? = null

    private var atcRetryAttempt = 0
    private var somaRetryAttempt = 0

    // Tokens to cancel pending reconnects per stream
    private val atcReconnectToken = Any()
    private val somaReconnectToken = Any()

    private val sleepTimer = SleepTimer()
    private val sleepToken = Any()
    private val _sleepRemaining = MutableStateFlow<Long?>(null)
    val sleepRemaining: StateFlow<Long?> = _sleepRemaining
    private var sleepGain = 1f
    private val activityDetector = AtcActivityDetector()
    private val activityToken = Any()
    private var atcDuckingEnabled = false
    private var atcDuckingDepth = 0.5f
    private var speechGain = 1f
    private lateinit var connectivityManager: ConnectivityManager
    private var validatedNetwork: Network? = null
    private var destroyed = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            handler.post {
                if (destroyed) return@post
                if (validated && validatedNetwork != network) {
                    validatedNetwork = network
                    retryNow(true)
                    retryNow(false)
                } else if (!validated && validatedNetwork == network) validatedNetwork = null
            }
        }
        override fun onLost(network: Network) {
            handler.post { if (validatedNetwork == network) validatedNetwork = null }
        }
    }

    fun setSleepTimer(durationMillis: Long) {
        sleepTimer.start(SystemClock.elapsedRealtime(), durationMillis)
        handler.removeCallbacksAndMessages(sleepToken)
        val alarm = PendingIntent.getService(this, 10,
            Intent(this, AudioService::class.java).setAction(ACTION_SLEEP_EXPIRED)
                .putExtra("deadline", sleepTimer.deadlineMillis!!),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, sleepTimer.deadlineMillis!!, alarm)
        tickSleepTimer()
    }

    fun cancelSleepTimer() {
        sleepTimer.cancel()
        handler.removeCallbacksAndMessages(sleepToken)
        PendingIntent.getService(this, 10,
            Intent(this, AudioService::class.java).setAction(ACTION_SLEEP_EXPIRED),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        _sleepRemaining.value = null
        sleepGain = 1f
        applyVolumes()
        updateNotification()
    }

    private fun tickSleepTimer() {
        val now = SystemClock.elapsedRealtime()
        val remaining = sleepTimer.remaining(now) ?: return
        _sleepRemaining.value = remaining
        sleepGain = sleepTimer.gain(now)
        applyVolumes()
        if (remaining == 0L) {
            android.util.Log.i("COMSAT.Audio", "SLEEP_EXPIRED")
            stopAll()
        }
        else {
            updateNotification()
            HandlerCompat.postDelayed(handler, { tickSleepTimer() }, sleepToken, 1_000)
        }
    }

    private fun tickActivity() {
        if (!atcDuckingEnabled || !_atcState.value.isActive || baseAtcVolume == 0f) {
            speechGain = 1f
            activityDetector.reset()
            applyVolumes()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val level = if (_atcState.value.isActive && baseAtcVolume > 0f)
            atcSpectrumProcessor.levelAt(now) else 0f
        speechGain = 1f - activityDetector.update(level, now) * atcDuckingDepth
        applyVolumes()
        HandlerCompat.postDelayed(handler, { tickActivity() }, activityToken, 100)
    }

    private fun updateActivityMonitoring() {
        handler.removeCallbacksAndMessages(activityToken)
        tickActivity()
    }

    /** Only RECONNECTING streams are eligible; pause/stop always win. */
    fun retryNow(isAtc: Boolean, resetAttempts: Boolean = true) {
        val state = if (isAtc) _atcState else _somaState
        if (state.value.status != StreamStatus.RECONNECTING) return
        val token = if (isAtc) atcReconnectToken else somaReconnectToken
        handler.removeCallbacksAndMessages(token)
        if (resetAttempts) {
            if (isAtc) atcRetryAttempt = 0 else somaRetryAttempt = 0
        }
        if (!requestFocus()) {
            state.value = StreamState(StreamStatus.PAUSED)
            updateNotification()
            return
        }
        state.value = StreamState(StreamStatus.LOADING)
        val url = if (isAtc) atcUrl else somaUrl
        if (!isAtc && somaNextTrackApiBase != null && url == null) fetchNextChainedTrack()
        else if (url != null) {
            val player = if (isAtc) atcPlayer else somaPlayer
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
        updateNotification()
    }

    // ─── Audio focus ──────────────────────────────────────────────────────────

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false
    private var ducked = false
    private val focusResumePlan = FocusResumePlan()

    private var baseAtcVolume = 1f
    private var baseSomaVolume = 1f

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusHeld = false
                pauseAll()
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusHeld = false
                focusResumePlan.remember(_atcState.value.canPause, _somaState.value.canPause)
                pauseAll(clearResumePlan = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                applyVolumes()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (focusRequest == null) return@OnAudioFocusChangeListener
                onFocusGranted()
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
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
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
        widgetStateScope.launch {
            settingsRepository.settings.collect { settings ->
                atcDuckingEnabled = settings.atcDuckingEnabled
                atcDuckingDepth = settings.atcDuckingDepth
                updateActivityMonitoring()
            }
        }
        widgetStateScope.launch {
            combine(atcState, somaState) { atc, soma -> atc.status to soma.status }
                .distinctUntilChanged()
                .collect { (atc, soma) -> playbackSnapshotRepository.updatePlayback(atc, soma) }
        }
        widgetStateScope.launch {
            atcState.collect { updateActivityMonitoring() }
        }
        widgetStateScope.launch {
            somaState.collect { updateLiveLink() }
        }
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
        if (intent.action == ACTION_SLEEP_EXPIRED) {
            if (intent.getLongExtra("deadline", -1) == sleepTimer.deadlineMillis &&
                sleepTimer.remaining(SystemClock.elapsedRealtime()) == 0L) stopAll()
            else if (_atcState.value.status == StreamStatus.IDLE &&
                _somaState.value.status == StreamStatus.IDLE) stopSelf(startId)
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
        destroyed = true
        cancelSleepTimer()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        widgetStateScope.cancel()
        playbackSnapshotRepository.clearPlayback()
        unregisterReceiver(noisyReceiver)
        // Invalidate callbacks as well as cancelling the socket: an HTTP
        // response may already be on its way back to the main thread.
        clearChainedPlayback()
        voicePlayer?.release()
        voicePlayer = null
        abandonFocus()
        mediaSession.release()
        compositePlayer.release()
        atcPlayer.release()
        somaPlayer.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestFocus(): Boolean {
        if (focusHeld) return true
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
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
        if (granted) {
            focusRequest = request
            // A synchronous grant need not also emit AUDIOFOCUS_GAIN.
            onFocusGranted()
        }
        return granted
    }

    private fun onFocusGranted() {
        focusHeld = true
        ducked = false
        applyVolumes()
        val interrupted = focusResumePlan.take()
        resumeAll(atc = interrupted.atc, soma = interrupted.soma)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        focusHeld = false
        focusResumePlan.clear()
        ducked = false
    }

    private fun applyVolumes() {
        val factor = (if (ducked) DUCK_FACTOR else 1f) * sleepGain
        atcPlayer.volume = baseAtcVolume * factor
        val underAtc =
            if (atcDuckingEnabled && baseAtcVolume > 0f && _atcState.value.isActive) speechGain else 1f
        somaPlayer.volume = baseSomaVolume * factor * underAtc *
            (if (voiceSounding) VOICE_DUCK_FACTOR else 1f)
        // The station host speaks on the ambient channel: at its level, and under a
        // controller's transmission like the music.
        voicePlayer?.volume = baseSomaVolume * factor * underAtc
    }

    // ─── Public controls ──────────────────────────────────────────────────────

    fun playAtc(url: String, label: String? = null) {
        focusResumePlan.cancelAtc()
        atcUrl = url
        atcLabel = label
        atcRetryAttempt = 0
        handler.removeCallbacksAndMessages(atcReconnectToken)
        atcPlayer.stop()
        _atcState.value = StreamState(StreamStatus.LOADING)
        atcPlayer.setMediaItem(MediaItem.fromUri(url))
        ensureForeground()
        if (requestFocus()) {
            atcPlayer.prepare()
            atcPlayer.play()
        } else {
            _atcState.value = StreamState(StreamStatus.PAUSED)
        }
        updateNotification()
    }

    fun stopAtc(keepForRetune: Boolean = false) {
        focusResumePlan.cancelAtc()
        atcUrl = null
        atcLabel = null
        atcRetryAttempt = 0
        handler.removeCallbacksAndMessages(atcReconnectToken)
        atcPlayer.stop()
        atcSpectrumProcessor.reset()
        _atcState.value = StreamState(StreamStatus.IDLE)
        if (!keepForRetune) maybeStopForeground()
        updateNotification()
    }

    fun playSoma(url: String, label: String? = null) {
        focusResumePlan.cancelSoma()
        clearChainedPlayback()
        somaUrl = url
        somaLabel = label
        somaRetryAttempt = 0
        handler.removeCallbacksAndMessages(somaReconnectToken)
        somaPlayer.stop()
        somaCarriedSelection = null
        showMusicTitle(null)
        _somaState.value = StreamState(StreamStatus.LOADING)
        somaPlayer.setMediaItem(MediaItem.fromUri(url))
        ensureForeground()
        if (requestFocus()) {
            somaPlayer.prepare()
            somaPlayer.play()
        } else {
            _somaState.value = StreamState(StreamStatus.PAUSED)
        }
        updateNotification()
    }

    // Track-chained station: no continuous stream URL; resolve the first track
    // via /next-track and keep chaining from the player's STATE_ENDED.
    fun playSomaChained(apiBase: String, label: String? = null) {
        focusResumePlan.cancelSoma()
        val carried = somaCarriedSelection?.takeIf { it.first == apiBase.trimEnd('/') }?.second
        somaCarriedSelection = null
        clearChainedPlayback()
        somaPendingSelection = carried
        // Do not let the previously selected SomaFM stream continue underneath
        // while the first chained track is being resolved.
        somaPlayer.stop()
        somaSpectrumProcessor.reset()
        somaNextTrackApiBase = apiBase.trimEnd('/')
        somaUrl = null
        somaLabel = label
        somaRetryAttempt = 0
        handler.removeCallbacksAndMessages(somaReconnectToken)
        showMusicTitle(null)
        _somaState.value = StreamState(StreamStatus.LOADING)
        ensureForeground()
        if (requestFocus()) fetchNextChainedTrack()
        else _somaState.value = StreamState(StreamStatus.PAUSED)
        updateNotification()
    }

    fun stopSoma(keepForRetune: Boolean = false) {
        focusResumePlan.cancelSoma()
        somaCarriedSelection = somaNextTrackApiBase?.let { base ->
            somaPendingSelection?.takeIf { it.requestId != null }?.let { base to it }
        }
        clearChainedPlayback()
        somaUrl = null
        somaLabel = null
        somaRetryAttempt = 0
        handler.removeCallbacksAndMessages(somaReconnectToken)
        somaPlayer.stop()
        somaSpectrumProcessor.reset()
        showMusicTitle(null)
        _somaState.value = StreamState(StreamStatus.IDLE)
        if (!keepForRetune) maybeStopForeground()
        updateNotification()
    }

    private fun clearChainedPlayback() {
        somaNextTrackApiBase = null
        somaCurrentTrackName = null
        somaPendingSelection = null
        somaNextTrackCall?.cancel()
        somaNextTrackCall = null
        // A retune may keep the same stream status, which the state collector cannot see.
        updateLiveLink()
    }

    // ─── Chained station live link: presence and the host's voice ─────────────

    /** The link lives while the chained station's music plays, across its track changes. */
    private fun updateLiveLink() {
        val base = somaNextTrackApiBase
        val status = _somaState.value.status
        val wanted = base.takeIf {
            status == StreamStatus.PLAYING || status == StreamStatus.BUFFERING ||
                (status == StreamStatus.LOADING && liveBase == base)
        }
        if (wanted == liveBase) return
        stopLiveLink()
        if (wanted != null && !destroyed) {
            liveBase = wanted
            liveHeartbeat()
            pollSay()
        }
    }

    private fun stopLiveLink() {
        if (liveBase == null && !speaking) return
        liveBase = null
        liveGeneration++
        handler.removeCallbacksAndMessages(liveHeartbeatToken)
        handler.removeCallbacksAndMessages(liveSayToken)
        handler.removeCallbacksAndMessages(voiceGapToken)
        handler.removeCallbacksAndMessages(voiceWatchToken)
        // A request still on its way must neither prolong the presence nor hold the service.
        liveHeartbeatCall?.cancel()
        liveHeartbeatCall = null
        liveSayCall?.cancel()
        liveSayCall = null
        // Start live again next time: lines spoken while nobody listened are not replayed.
        sayFeed.reset()
        voiceQueue.clear()
        if (speaking) {
            speaking = false
            voiceSounding = false
            voicePlayer?.stop()
            spokenLine = null
            publishNowPlaying()
            applyVolumes()
        }
    }

    private fun showMusicTitle(title: String?) {
        somaMusicTitle = title
        publishNowPlaying()
    }

    private fun publishNowPlaying() {
        _somaNowPlaying.value = spokenLine?.takeIf { voiceSounding } ?: somaMusicTitle
    }

    /** Presence: the station's token comes as a cookie with its page and goes back in a header. */
    private fun liveHeartbeat() {
        val base = liveBase ?: return
        val generation = liveGeneration
        val token = liveToken
        val request = if (token == null) {
            Request.Builder().url("$base/").cacheControl(CacheControl.FORCE_NETWORK).build()
        } else {
            Request.Builder()
                .url(stationUrl(base, "/listener-ping").toHttpUrl().newBuilder()
                    .addQueryParameter("id", liveSession).build())
                .post(ByteArray(0).toRequestBody(null))
                .header("Cookie", "${ListenerToken.COOKIE}=$token")
                .header(ListenerToken.HEADER, token)
                .build()
        }
        val call = okHttpClient.newCall(request)
        liveHeartbeatCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    if (liveHeartbeatCall === call) liveHeartbeatCall = null
                    scheduleLive(generation, liveHeartbeatToken, HEARTBEAT_MS, ::liveHeartbeat)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val status = response.code
                val cookies = response.headers("Set-Cookie")
                response.close()
                handler.post {
                    if (liveHeartbeatCall === call) liveHeartbeatCall = null
                    if (generation != liveGeneration) return@post
                    if (token == null) {
                        liveToken = ListenerToken.fromSetCookie(cookies)
                        // Announce the listener at once instead of a period later.
                        if (liveToken != null) return@post liveHeartbeat()
                    } else if (status == 401 || status == 403) {
                        liveToken = null
                    }
                    scheduleLive(generation, liveHeartbeatToken, HEARTBEAT_MS, ::liveHeartbeat)
                }
            }
        })
    }

    /** The host's voice: a feed of short clips, started live and polled while the music plays. */
    private fun pollSay() {
        val base = liveBase ?: return
        val generation = liveGeneration
        val request = Request.Builder().url(stationUrl(base, sayFeed.path()))
            .cacheControl(CacheControl.FORCE_NETWORK).build()
        val call = okHttpClient.newCall(request)
        liveSayCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    if (liveSayCall === call) liveSayCall = null
                    scheduleLive(generation, liveSayToken, SAY_POLL_MS, ::pollSay)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.use { resp ->
                    if (!resp.isSuccessful) null
                    else runCatching { JSONObject(resp.body.string()) }.getOrNull()
                }
                handler.post {
                    if (liveSayCall === call) liveSayCall = null
                    if (generation != liveGeneration) return@post
                    if (json != null && !sayFeed.ready) {
                        // Only a real counter marks "now"; an answer without one would
                        // start at zero and replay the station's stored lines.
                        (json.opt("latest") as? Number)?.toLong()?.takeIf { it >= 0 }
                            ?.let(sayFeed::startLive)
                    } else if (json != null) {
                        val array = json.optJSONArray("clips")
                        val clips = (0 until (array?.length() ?: 0))
                            .mapNotNull { array?.optJSONObject(it) }
                            .map { SayClip(it.optLong("seq"), it.optString("url"), it.optString("text")) }
                        voiceQueue.addAll(sayFeed.accept(clips))
                        playNextVoice()
                    }
                    scheduleLive(generation, liveSayToken, SAY_POLL_MS, ::pollSay)
                }
            }
        })
    }

    private fun scheduleLive(generation: Int, token: Any, delayMs: Long, action: () -> Unit) {
        if (generation != liveGeneration || liveBase == null) return
        handler.removeCallbacksAndMessages(token)
        HandlerCompat.postDelayed(handler, {
            if (generation == liveGeneration) action()
        }, token, delayMs)
    }

    private fun playNextVoice() {
        // One line at a time, with a breath between two lines.
        if (speaking || SystemClock.elapsedRealtime() < voiceGapUntil) return
        val base = liveBase ?: return
        val clip = voiceQueue.removeFirstOrNull() ?: return
        val url = resolveChainedTrackUrl(base, clip.url) ?: return playNextVoice()
        val player = voicePlayer ?: buildVoicePlayer().also { voicePlayer = it }
        speaking = true
        // Shown and ducked for only once the voice is audible (voiceSoundingChanged).
        spokenLine = clip.text.trim().takeIf { it.isNotEmpty() }?.let { text ->
            (somaLabel?.substringBefore(' ')?.uppercase() ?: "HOST") + ": " + text.take(160)
        }
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        watchSilentVoice()
    }

    /** A clip that does not arrive, or stalls, must not hold the queue: give it up after a while. */
    private fun watchSilentVoice() {
        val generation = liveGeneration
        handler.removeCallbacksAndMessages(voiceWatchToken)
        HandlerCompat.postDelayed(handler, {
            if (generation == liveGeneration && speaking && !voiceSounding) {
                voicePlayer?.stop()
                voiceFinished()
            }
        }, voiceWatchToken, VOICE_SILENCE_LIMIT_MS)
    }

    private fun voiceSoundingChanged(sounding: Boolean) {
        if (!speaking || voiceSounding == sounding) return
        voiceSounding = sounding
        // The limit counts silence only: a line that is sounding plays to its end.
        if (sounding) handler.removeCallbacksAndMessages(voiceWatchToken) else watchSilentVoice()
        publishNowPlaying()
        applyVolumes()
        updateNotification()
    }

    private fun voiceFinished() {
        if (!speaking) return
        speaking = false
        voiceSounding = false
        spokenLine = null
        handler.removeCallbacksAndMessages(voiceWatchToken)
        publishNowPlaying()
        applyVolumes()
        updateNotification()
        val generation = liveGeneration
        voiceGapUntil = SystemClock.elapsedRealtime() + VOICE_GAP_MS
        // Its own token: a feed answer rescheduling the poll must not cancel the next line.
        HandlerCompat.postDelayed(handler, {
            if (generation == liveGeneration) playNextVoice()
        }, voiceGapToken, VOICE_GAP_MS)
    }

    private fun buildVoicePlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ false
            )
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) voiceFinished()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = voiceSoundingChanged(isPlaying)

            override fun onPlayerError(error: PlaybackException) = voiceFinished()
        })
        return player
    }

    // Same cleanup the station's own web player applies to file names
    private fun chainedTrackTitle(name: String): String? = name
        .removeSuffix(".mp3")
        .replace(Regex("[-_]+"), " ")
        .trim()
        .ifBlank { null }

    private fun fetchNextChainedTrack() {
        val apiBase = somaNextTrackApiBase ?: return
        // null distinguishes "waiting for the next file" from a paused file.
        // resumeAll() can then restart the API request instead of replaying an
        // already-ended track.
        somaUrl = null
        somaNextTrackCall?.cancel()
        val selection = somaPendingSelection ?: ChainedTrackSelection(somaCurrentTrackName)
            .also { somaPendingSelection = it }
        val needsClock = selection.requestId == null
        val call = okHttpClient.newCall(Request.Builder().url(selection.url(apiBase))
            .cacheControl(CacheControl.FORCE_NETWORK).build())
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
                val status = response.code
                val httpDate = response.header("Date")
                val json = response.use { resp ->
                    if (!resp.isSuccessful) null
                    else runCatching { JSONObject(resp.body.string()) }.getOrNull()
                }
                handler.post {
                    if (somaNextTrackCall !== call) return@post
                    somaNextTrackCall = null
                    // Station may have been stopped, paused or retuned while
                    // the HTTP callback was in flight.
                    if (somaNextTrackApiBase != apiBase ||
                        _somaState.value.status != StreamStatus.LOADING
                    ) return@post
                    if (somaPendingSelection !== selection) return@post
                    if (needsClock) {
                        if (status in 200..299 && selection.synchronize(
                                json?.optLong("server_time_ms")?.takeIf { it > 0 }, httpDate
                            )) fetchNextChainedTrack()
                        else onChainedFetchFailed()
                        return@post
                    }
                    // Only an explicit expiry retires a lost selection. A timeout,
                    // 429 or 5xx retries the same id and the same current parameter.
                    if (status == 410) somaPendingSelection = null
                    val path = json?.optString("url")?.takeIf { it.isNotBlank() && it != "null" }
                    val trackUrl = path?.let { resolveChainedTrackUrl(apiBase, it) }
                    if (trackUrl == null) {
                        // A successful empty-pool response selected no file; allow
                        // a later retry to ask again rather than cache emptiness.
                        if (status in 200..299 && json != null) somaPendingSelection = null
                        onChainedFetchFailed()
                    } else {
                        somaPendingSelection = null
                        playChainedTrack(trackUrl, json.optString("name")
                            .ifBlank { path.orEmpty().substringAfterLast('/') })
                    }
                }
            }
        })
    }

    private fun playChainedTrack(url: String, name: String) {
        somaUrl = url
        somaCurrentTrackName = name
        // The host may be speaking across the track change; the spoken line stays shown.
        showMusicTitle(chainedTrackTitle(name))
        _somaState.value = StreamState(StreamStatus.LOADING)
        somaPlayer.setMediaItem(MediaItem.fromUri(url))
        somaPlayer.prepare()
        somaPlayer.play()
        updateNotification()
    }

    private fun onChainedFetchFailed() {
        if (somaNextTrackApiBase == null) return
        val attempt = ++somaRetryAttempt
        val delayMs = retryDelayMillis(attempt)
        handler.removeCallbacksAndMessages(somaReconnectToken)
        _somaState.value = StreamState(StreamStatus.RECONNECTING, "STREAM OFFLINE",
            SystemClock.elapsedRealtime() + delayMs)
        android.util.Log.i("COMSAT.Audio", "AMBIENT RECONNECTING attempt=$attempt retryMs=$delayMs")
        HandlerCompat.postDelayed(handler, {
            if (_somaState.value.status == StreamStatus.RECONNECTING) {
                retryNow(false, resetAttempts = false)
            }
        }, somaReconnectToken, delayMs)
        updateNotification()
    }

    fun setAtcVolume(v: Float) {
        baseAtcVolume = v.coerceIn(0f, 1f)
        updateActivityMonitoring()
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

    private fun pauseAll(clearResumePlan: Boolean = true) {
        // A transport pause or unplug cancels automatic focus recovery.
        if (clearResumePlan) focusResumePlan.clear()
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

    private fun resumeAll(atc: Boolean = true, soma: Boolean = true) {
        val resumeAtc = atc && _atcState.value.status == StreamStatus.PAUSED
        val resumeSoma = soma && _somaState.value.status == StreamStatus.PAUSED
        if (!resumeAtc && !resumeSoma) return
        if (!requestFocus()) return
        if (atc) focusResumePlan.cancelAtc()
        if (soma) focusResumePlan.cancelSoma()
        if (resumeAtc) resumeStream(atcPlayer, atcUrl, _atcState, rejoinLiveEdge = true)
        if (resumeSoma && somaNextTrackApiBase != null && somaUrl == null &&
            _somaState.value.status == StreamStatus.PAUSED
        ) {
            // Chained station paused before its first track resolved
            _somaState.value = StreamState(StreamStatus.LOADING)
            fetchNextChainedTrack()
        } else if (resumeSoma) {
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
        _stopGeneration.value++
        cancelSleepTimer()
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
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
                    if (!stateFlow.value.canPause) return
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
                        showMusicTitle(title)
                    }
                    updateNotification()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val stateFlow = if (isAtc) _atcState else _somaState
                // Paused players can still fail while filling their buffer.
                // Leave them paused; resumeStream will prepare the idle player.
                if (!stateFlow.value.canPause) return
                // A paused file may have left the station pool after its lease
                // expired. Retrying that 404/410 forever cannot recover playback.
                // Network and decoder failures still retry the selected URL.
                if (!isAtc && somaNextTrackApiBase != null &&
                    generateSequence<Throwable>(error) { it.cause }
                        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                        .any { it.responseCode == 404 || it.responseCode == 410 }
                ) somaUrl = null
                val url = if (isAtc) atcUrl else somaUrl
                val attempt = if (isAtc) ++atcRetryAttempt else ++somaRetryAttempt
                stateFlow.value = StreamState(StreamStatus.RECONNECTING, friendlyErrorMessage(error))
                // Back-off reconnect: 5 s, then 10 s, then 30 s
                scheduleReconnect(url, stateFlow, attempt, isAtc)
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
        url: String?,
        stateFlow: MutableStateFlow<StreamState>,
        attempt: Int,
        isAtc: Boolean
    ) {
        if (url == null && (isAtc || somaNextTrackApiBase == null)) return
        val delayMs = retryDelayMillis(attempt)
        val token = if (isAtc) atcReconnectToken else somaReconnectToken
        handler.removeCallbacksAndMessages(token)
        stateFlow.value = stateFlow.value.copy(retryAtMillis = SystemClock.elapsedRealtime() + delayMs)
        android.util.Log.i("COMSAT.Audio",
            "${if (isAtc) "ATC" else "AMBIENT"} RECONNECTING attempt=$attempt retryMs=$delayMs")
        HandlerCompat.postDelayed(handler, {
            retryNow(isAtc, resetAttempts = false)
        }, token, delayMs)
        updateNotification()
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
            cancelSleepTimer()
            abandonFocus()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission") // MediaSession notifications are exempt from POST_NOTIFICATIONS.
    private fun updateNotification() {
        // Posted so it never runs re-entrantly inside a CompositePlayer handle* call
        handler.post { compositePlayer.refresh() }
        // Don't resurrect a notification the service is about to drop
        if (_atcState.value.status == StreamStatus.IDLE &&
            _somaState.value.status == StreamStatus.IDLE
        ) return
        // buildNotification always includes MediaStyle and this service's session
        // token. Let Android apply its media exemption and channel preferences;
        // checking the permission here leaves an already-visible notification stale.
        NotificationManagerCompat.from(this)
            .notify(ComsatApp.NOTIFICATION_ID, buildNotification())
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
        val labelsText = when {
            activeLabels.isEmpty() -> getString(R.string.notification_text)
            anyActive -> activeLabels.joinToString(" + ")
            else -> "${getString(R.string.notification_paused)} · ${activeLabels.joinToString(" + ")}"
        }

        val contentText = labelsText + (_sleepRemaining.value?.let { " · ${sleepTimeText(it)}" } ?: "")
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
        private const val ACTION_SLEEP_EXPIRED = "com.comsat.audio.SLEEP_EXPIRED"
        const val ACTION_STOP_ALL = "com.comsat.audio.action.STOP_ALL"

        private const val DUCK_FACTOR = 0.3f

        // Chained station live link
        private const val VOICE_DUCK_FACTOR = 0.35f
        private const val HEARTBEAT_MS = 5_000L
        // The station's web page asks every 1.4 s; a phone asks less often and hears
        // a line at most two seconds later.
        private const val SAY_POLL_MS = 2_000L
        private const val VOICE_GAP_MS = 180L
        private const val VOICE_SILENCE_LIMIT_MS = 30_000L

        private val PAUSABLE_STATUSES = setOf(
            StreamStatus.PLAYING,
            StreamStatus.BUFFERING,
            StreamStatus.LOADING,
            StreamStatus.RECONNECTING
        )
    }
}
