package io.thernal.archkit.core.presentation.impl.plugin.intent

import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.intent.IntentHandler
import io.thernal.archkit.core.presentation.api.plugin.intent.ViewIntent
import io.thernal.archkit.core.presentation.impl.plugin.PRESENTATION_LOG_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Intents are handled one at a time, in the order they were posted, on the ViewModel's scope — a
 * channel rather than a shared flow, so an intent posted before anything collects is not lost.
 */
internal class IntentHandlerImpl<I : ViewIntent>(
    private val owner: String,
    private val logger: PresentationLogger?,
    scope: CoroutineScope,
    private val onError: (Throwable) -> Unit,
    private val onIntent: (I) -> Unit,
) : IntentHandler<I> {
    private val intents = Channel<I>(Channel.UNLIMITED)

    init {
        intents.receiveAsFlow().onEach(::handle).launchIn(scope)
    }

    override fun postIntent(intent: I) {
        logger?.log(tag = PRESENTATION_LOG_TAG, message = "$owner intent · $intent", error = null)
        intents.trySend(intent)
    }

    @Suppress("TooGenericExceptionCaught") // A failing intent is reported, never allowed to end the loop.
    private fun handle(intent: I) {
        try {
            onIntent(intent)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            logger?.log(tag = PRESENTATION_LOG_TAG, message = "$owner intent failed · $intent", error = error)
            onError(error)
        }
    }
}
