package io.thernal.archkit.core.presentation.api.plugin.intent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.core.presentation.api.plugin.PluginContext
import io.thernal.archkit.core.presentation.api.plugin.factory
import io.thernal.archkit.core.presentation.api.plugin.ownerName
import io.thernal.archkit.core.presentation.api.viewmodel.reportUncaughtError

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
        owner = viewModel.ownerName(),
        log = log,
        scope = viewModel.viewModelScope,
        onError = { error -> viewModel.reportUncaughtError(error) },
        onIntent = onIntent,
    )
}

fun <I : ViewIntent> IntentPlugin<I>.postIntent(intent: I) {
    intentHandler.postIntent(intent)
}
