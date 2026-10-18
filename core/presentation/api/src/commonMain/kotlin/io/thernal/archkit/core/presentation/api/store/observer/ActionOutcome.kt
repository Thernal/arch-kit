package io.thernal.archkit.core.presentation.api.store.observer

import kotlin.time.Duration

sealed interface ActionOutcome {
    /** `Queue`: another action with the key runs; this one starts after it. */
    data object Queued : ActionOutcome

    /** `Ignore`: another action with the key runs, so this one never starts. */
    data object Dropped : ActionOutcome

    data object Started : ActionOutcome

    data class Finished(
        val duration: Duration,
    ) : ActionOutcome

    /** `Latest` started a newer one, or the store went away. */
    data class Cancelled(
        val duration: Duration,
    ) : ActionOutcome

    data class Failed(
        val error: Throwable,
        val duration: Duration,
    ) : ActionOutcome
}
