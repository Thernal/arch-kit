package io.thernal.archkit.core.presentation.api.plugin.intent

import io.thernal.archkit.core.presentation.api.plugin.PluginFactory
import kotlinx.coroutines.CoroutineScope

interface IntentFactory : PluginFactory {
    fun <I : ViewIntent> create(
        owner: String,
        log: Boolean,
        scope: CoroutineScope,
        onError: (Throwable) -> Unit,
        onIntent: (I) -> Unit,
    ): IntentHandler<I>
}
