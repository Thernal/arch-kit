package io.thernal.archkit.core.domain.concurrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/** Runs only the last action of a burst, [duration] after the burst stops: search-as-you-type. */
class Debouncer(
    private val duration: Duration,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    operator fun invoke(action: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(duration)
            action()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
