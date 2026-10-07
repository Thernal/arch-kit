package io.thernal.archkit.core.domain.safecall

/** Holds the app's [FailureReporter]; `safeCall` reports through it. */
object FailureReporting {
    var reporter: FailureReporter = FailureReporter { _, _ -> }
}
