package io.thernal.archkit.core.presentation.api.presentation.effect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.safecall.SafeCallScope
import io.thernal.archkit.core.domain.safecall.runSafeCall
import io.thernal.archkit.core.presentation.api.presentation.model.BaseEffect
import io.thernal.archkit.core.presentation.api.presentation.model.MessageEffect
import io.thernal.archkit.core.presentation.api.presentation.model.MessageType
import io.thernal.archkit.core.presentation.api.presentation.model.NavEffect
import io.thernal.archkit.core.presentation.api.presentation.model.ViewEffect
import io.thernal.archkit.core.presentation.api.presentation.plugin.PluginContext
import io.thernal.archkit.core.presentation.api.presentation.plugin.PluginFactory
import io.thernal.archkit.core.presentation.api.presentation.plugin.factory
import io.thernal.archkit.core.presentation.api.presentation.string.UiString
import io.thernal.archkit.core.presentation.api.presentation.string.toUiString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** Delivers effects to the screen, each once. Closed with its ViewModel. */
interface EffectHandler<E : ViewEffect> : AutoCloseable {
    val effects: Flow<BaseEffect>

    fun launchEffect(effect: E)

    fun launchBaseEffect(effect: BaseEffect)
}

interface EffectFactory : PluginFactory {
    fun <E : ViewEffect> create(
        owner: String,
        log: Boolean,
        scope: CoroutineScope,
    ): EffectHandler<E>
}

interface EffectPlugin<E : ViewEffect> : PluginContext {
    val effectHandler: EffectHandler<E>

    /** `safeCall` whose failure is shown to the user as an error message. */
    context(_: ViewModel)
    suspend fun <T> safeCall(block: suspend context(SafeCallScope) () -> T): Result<T> {
        return runSafeCall(
            block = block,
            onFailure = { failure -> effectHandler.launchBaseEffect(MessageEffect(failure.toUiString())) },
        )
    }
}

/** The ViewModel's effect handler, closed with it. Compiles only in a ViewModel. */
context(context: PluginContext, viewModel: ViewModel)
fun <E : ViewEffect> EffectHandler(log: Boolean = true): EffectHandler<E> {
    return context.plugins.factory<EffectFactory>()
        .create<E>(owner = viewModel::class.simpleName.orEmpty(), log = log, scope = viewModel.viewModelScope)
        .also { viewModel.addCloseable(it) }
}

context(_: ViewModel, plugin: EffectPlugin<E>)
fun <E : ViewEffect> launchEffect(effect: E) {
    plugin.effectHandler.launchEffect(effect)
}

context(_: ViewModel, plugin: EffectPlugin<*>)
fun sendMessage(
    message: UiString,
    type: MessageType = MessageType.ERROR,
) {
    plugin.effectHandler.launchBaseEffect(MessageEffect(message = message, type = type))
}

context(_: ViewModel, plugin: EffectPlugin<*>)
fun sendMessage(
    failure: Failure,
    type: MessageType = MessageType.ERROR,
) {
    sendMessage(message = failure.toUiString(), type = type)
}

/**
 * Navigates from a ViewModel: the command runs on the screen's navigator, of type [N]. An app
 * usually defines a one-line shorthand for its own navigator type:
 * `fun navigation(command: Navigator.() -> Unit) = navigation<Navigator>(command)`.
 */
context(_: ViewModel, plugin: EffectPlugin<*>)
fun <N> navigation(command: N.() -> Unit) {
    plugin.effectHandler.launchBaseEffect(NavEffect(command))
}
