package io.thernal.archkit.core.presentation.api.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
