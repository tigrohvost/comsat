package com.comsat.audio.service

/** All times are monotonic milliseconds, including time spent in deep sleep. */
class SleepTimer {
    var deadlineMillis: Long? = null
        private set

    fun start(nowMillis: Long, durationMillis: Long) {
        require(durationMillis in 1..86_400_000L)
        deadlineMillis = nowMillis + durationMillis
    }

    fun cancel() { deadlineMillis = null }
    fun remaining(nowMillis: Long): Long? = deadlineMillis?.let { (it - nowMillis).coerceAtLeast(0) }
    fun gain(nowMillis: Long): Float = remaining(nowMillis)?.let {
        (it / 30_000f).coerceIn(0f, 1f)
    } ?: 1f
}

fun sleepTimeText(remainingMillis: Long): String {
    val seconds = (remainingMillis.coerceAtLeast(0) + 999) / 1000
    return "SLP %d:%02d".format(java.util.Locale.US, seconds / 60, seconds % 60)
}

fun retryDelayMillis(attempt: Int): Long = when (attempt) {
    1 -> 5_000L
    2 -> 10_000L
    else -> 30_000L
}
