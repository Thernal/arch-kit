package io.thernal.archkit.core.presentation.api.presentation.intent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.core.presentation.api.presentation.model.ViewIntent
import io.thernal.archkit.core.presentation.api.presentation.plugin.PluginContext
import io.thernal.archkit.core.presentation.api.presentation.plugin.PluginFactory
import io.thernal.archkit.core.presentation.api.presentation.plugin.factory
import io.thernal.archkit.core.presentation.api.presentation.viewmodel.reportUncaughtError
import kotlinx.coroutines.CoroutineScope

interface IntentHandler<I : ViewIntent> {
    fun postIntent(intent: I)
}

interface IntentFactory : PluginFactory {
    fun <I : ViewIntent> create(
        owner: String,
        log: Boolean,
        scope: CoroutineScope,
        onError: (Throwable) -> Unit,
        onIntent: (I) -> Unit,
    ): IntentHandler<I>
}

interface IntentPlugin<I : ViewIntent> : PluginContext {
    val intentHandler: IntentHandler<I>
}

/**
 * The ViewModel's intent handler: [onIntent] runs for each intent in order; an exception it throws is
 * reported (and shown, if the ViewModel is an effect plugin) instead of crashing. Compiles only in a
 * ViewModel.
 */
context(context: PluginContext, viewModel: ViewModel)
fun <I : ViewIntent> IntentHandler(
    log: Boolean = true,
    onIntent: (I) -> Unit,
): IntentHandler<I> {
    return context.plugins.factory<IntentFactory>().create(
        owner = viewModel::class.simpleName.orEmpty(),
        log = log,
        scope = viewModel.viewModelScope,
        onError = { error -> viewModel.reportUncaughtError(error) },
        onIntent = onIntent,
    )
}

fun <I : ViewIntent> IntentPlugin<I>.postIntent(intent: I) {
    intentHandler.postIntent(intent)
}
