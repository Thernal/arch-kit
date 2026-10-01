package io.thernal.archkit.core.presentation.api.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.safecall.FailureReporting
import io.thernal.archkit.core.domain.safecall.safeCallFlow
import io.thernal.archkit.core.domain.usecase.FlowUseCase
import io.thernal.archkit.core.presentation.api.presentation.effect.EffectPlugin
import io.thernal.archkit.core.presentation.api.presentation.model.MessageEffect
import io.thernal.archkit.core.presentation.api.presentation.string.toUiString
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * `viewModelScope.launch`, except an uncaught exception is reported and — in an effect plugin — shown
 * to the user as a message instead of crashing the app. Use it instead of the raw scope.
 */
fun ViewModel.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    val handled = if (context[CoroutineExceptionHandler] == null) {
        context + CoroutineExceptionHandler { _, error -> reportUncaughtError(error) }
    } else {
        context
    }
    return viewModelScope.launch(context = handled, start = start, block = block)
}

fun <T> ViewModel.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T,
): Deferred<T> {
    return viewModelScope.async(context = context, start = start, block = block)
}

/** Reports [error] and, when this ViewModel shows effects, shows it as an error message. */
fun ViewModel.reportUncaughtError(error: Throwable) {
    FailureReporting.reporter.report(error = error, isUnexpected = error !is Failure)
    (this as? EffectPlugin<*>)?.effectHandler?.launchBaseEffect(MessageEffect(Failure.of(error).toUiString()))
}

/** Runs a [FlowUseCase] from a ViewModel: every value as a success, a failure as the last element. */
context(_: ViewModel)
operator fun <Input, Output> FlowUseCase<Input, Output>.invoke(params: Input): Flow<Result<Output>> {
    return safeCallFlow { execute(params) }
}

context(_: ViewModel)
operator fun <Output> FlowUseCase<Unit, Output>.invoke(): Flow<Result<Output>> {
    return invoke(Unit)
}
