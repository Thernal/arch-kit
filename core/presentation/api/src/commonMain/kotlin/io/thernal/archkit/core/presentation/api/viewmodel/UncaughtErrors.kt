package io.thernal.archkit.core.presentation.api.viewmodel

import androidx.lifecycle.ViewModel
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.safecall.FailureReporting
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectPlugin
import io.thernal.archkit.core.presentation.api.plugin.effect.message.MessageEffect
import io.thernal.archkit.core.presentation.api.string.toUiString

/** Reports [error] and, when this ViewModel shows effects, shows it as an error message. */
fun ViewModel.reportUncaughtError(error: Throwable) {
    FailureReporting.reporter.report(error = error, isUnexpected = error !is Failure)
    (this as? EffectPlugin<*>)?.effectHandler?.launchBaseEffect(MessageEffect(Failure.of(error).toUiString()))
}
