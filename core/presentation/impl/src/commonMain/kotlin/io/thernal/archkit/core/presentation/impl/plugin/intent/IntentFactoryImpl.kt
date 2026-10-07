package io.thernal.archkit.core.presentation.impl.plugin.intent

import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.intent.IntentFactory
import io.thernal.archkit.core.presentation.api.plugin.intent.IntentHandler
import io.thernal.archkit.core.presentation.api.plugin.intent.ViewIntent
import kotlinx.coroutines.CoroutineScope

class IntentFactoryImpl(
    private val logger: PresentationLogger? = null,
) : IntentFactory {
    override fun <I : ViewIntent> create(
        owner: String,
        log: Boolean,
        scope: CoroutineScope,
        onError: (Throwable) -> Unit,
        onIntent: (I) -> Unit,
    ): IntentHandler<I> {
        return IntentHandlerImpl(
            owner = owner,
            logger = logger.takeIf { log },
            scope = scope,
            onError = onError,
            onIntent = onIntent,
        )
    }
}
