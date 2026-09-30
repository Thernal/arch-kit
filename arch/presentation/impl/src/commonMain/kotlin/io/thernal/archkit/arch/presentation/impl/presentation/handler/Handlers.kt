package io.thernal.archkit.arch.presentation.impl.presentation.handler

import io.thernal.archkit.arch.event.api.domain.BaseEvent
import io.thernal.archkit.arch.event.api.domain.EventBus
import io.thernal.archkit.arch.presentation.api.presentation.effect.EffectFactory
import io.thernal.archkit.arch.presentation.api.presentation.effect.EffectHandler
import io.thernal.archkit.arch.presentation.api.presentation.event.EventFactory
import io.thernal.archkit.arch.presentation.api.presentation.event.EventHandler
import io.thernal.archkit.arch.presentation.api.presentation.intent.IntentFactory
import io.thernal.archkit.arch.presentation.api.presentation.intent.IntentHandler
import io.thernal.archkit.arch.presentation.api.presentation.model.BaseEffect
import io.thernal.archkit.arch.presentation.api.presentation.model.ViewEffect
import io.thernal.archkit.arch.presentation.api.presentation.model.ViewIntent
import io.thernal.archkit.arch.presentation.api.presentation.model.ViewState
import io.thernal.archkit.arch.presentation.api.presentation.plugin.PresentationLogger
import io.thernal.archkit.arch.presentation.api.presentation.state.StateFactory
import io.thernal.archkit.arch.presentation.api.presentation.state.StateHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

private const val TAG = "VIEWMODEL"

class DefaultStateFactory(
    private val logger: PresentationLogger? = null,
) : StateFactory {
    override fun <S : ViewState> create(
        owner: String,
        log: Boolean,
        initializer: () -> S,
    ): StateHandler<S> {
        return DefaultStateHandler(owner = owner, logger = logger.takeIf { log }, initial = initializer())
    }
}

internal class DefaultStateHandler<S : ViewState>(
    private val owner: String,
    private val logger: PresentationLogger?,
    initial: S,
) : StateHandler<S> {
    private val mutableState = MutableStateFlow(initial)

    override val state: StateFlow<S> = mutableState.asStateFlow()
    override val currentState: S get() = mutableState.value

    override fun setState(reducer: S.() -> S) {
        mutableState.update { old ->
            old.reducer().also { new -> logger?.log(tag = TAG, message = "$owner state · $old → $new", error = null) }
        }
    }
}

class DefaultIntentFactory(
    private val logger: PresentationLogger? = null,
) : IntentFactory {
    override fun <I : ViewIntent> create(
        owner: String,
        log: Boolean,
        scope: CoroutineScope,
        onError: (Throwable) -> Unit,
        onIntent: (I) -> Unit,
    ): IntentHandler<I> {
        return DefaultIntentHandler(
            owner = owner,
            logger = logger.takeIf { log },
            scope = scope,
            onError = onError,
            onIntent = onIntent,
        )
    }
}

/**
 * Intents are handled one at a time, in the order they were posted, on the ViewModel's scope — a
 * channel rather than a shared flow, so an intent posted before anything collects is not lost.
 */
internal class DefaultIntentHandler<I : ViewIntent>(
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
        logger?.log(tag = TAG, message = "$owner intent · $intent", error = null)
        intents.trySend(intent)
    }

    @Suppress("TooGenericExceptionCaught") // A failing intent is reported, never allowed to end the loop.
    private fun handle(intent: I) {
        try {
            onIntent(intent)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            logger?.log(tag = TAG, message = "$owner intent failed · $intent", error = error)
            onError(error)
        }
    }
}

class DefaultEffectFactory(
    private val logger: PresentationLogger? = null,
) : EffectFactory {
    override fun <E : ViewEffect> create(
        owner: String,
        log: Boolean,
        scope: CoroutineScope,
    ): EffectHandler<E> {
        return DefaultEffectHandler(owner = owner, logger = logger.takeIf { log })
    }
}

/**
 * Effects wait in a buffered channel until the screen collects them, and each goes to one collector,
 * once: a message is not shown twice after a rotation, and one sent while the screen is paused is shown
 * when it resumes.
 */
internal class DefaultEffectHandler<E : ViewEffect>(
    private val owner: String,
    private val logger: PresentationLogger?,
) : EffectHandler<E> {
    private val channel = Channel<BaseEffect>(Channel.BUFFERED)

    override val effects: Flow<BaseEffect> = channel.receiveAsFlow()

    override fun launchEffect(effect: E) {
        launchBaseEffect(effect)
    }

    override fun launchBaseEffect(effect: BaseEffect) {
        logger?.log(tag = TAG, message = "$owner effect · $effect", error = null)
        channel.trySend(effect)
    }

    override fun close() {
        channel.close()
    }
}

class DefaultEventFactory(
    private val eventBus: EventBus,
    private val logger: PresentationLogger? = null,
) : EventFactory {
    override fun create(
        owner: String,
        scope: CoroutineScope,
    ): EventHandler {
        return DefaultEventHandler(eventBus = eventBus, scope = scope, owner = owner, logger = logger)
    }
}

internal class DefaultEventHandler(
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val owner: String,
    private val logger: PresentationLogger?,
) : EventHandler {
    override fun fire(event: BaseEvent) {
        logger?.log(tag = TAG, message = "$owner fired · $event", error = null)
        scope.launch { eventBus.fire(event) }
    }

    override fun <T : BaseEvent> observe(
        type: KClass<T>,
        onEvent: suspend (T) -> Unit,
    ): Job {
        return eventBus.observe(type)
            .onEach { event ->
                logger?.log(tag = TAG, message = "$owner caught · $event", error = null)
                onEvent(event)
            }
            .launchIn(scope)
    }
}
