package io.thernal.archkit.core.presentation.api.viewmodel

import androidx.lifecycle.ViewModel
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.safecall.FailureReporting

/** Reports [error] to `FailureReporting`; a [Failure] is expected, anything else is not. */
fun ViewModel.reportUncaughtError(error: Throwable) {
    FailureReporting.reporter.report(error = error, isUnexpected = error !is Failure)
}
