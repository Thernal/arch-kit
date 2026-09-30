package io.thernal.archkit.arch.domain.concurrent

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Runs the first action and drops the rest for [duration]: a double-tapped submit button. */
class Throttler(
    private val duration: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var lastRun: TimeMark? = null

    fun tryAcquire(): Boolean {
        if (lastRun?.elapsedNow()?.let { it < duration } == true) {
            return false
        }
        lastRun = timeSource.markNow()
        return true
    }

    operator fun invoke(action: () -> Unit) {
        if (tryAcquire()) {
            action()
        }
    }
}
