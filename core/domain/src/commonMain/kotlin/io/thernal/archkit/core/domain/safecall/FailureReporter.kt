package io.thernal.archkit.core.domain.safecall

/**
 * Where failures caught by [safeCall] are reported — a crash reporter, a debug console. Set once at
 * startup ([FailureReporting.reporter]); by default nothing is reported.
 */
fun interface FailureReporter {
    /** [isUnexpected] is true for anything that was not already a `Failure`. */
    fun report(
        error: Throwable,
        isUnexpected: Boolean,
    )
}
