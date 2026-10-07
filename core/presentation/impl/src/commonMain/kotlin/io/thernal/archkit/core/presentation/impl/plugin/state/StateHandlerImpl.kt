package io.thernal.archkit.core.presentation.impl.plugin.state

import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.state.StateHandler
import io.thernal.archkit.core.presentation.api.plugin.state.ViewState
import io.thernal.archkit.core.presentation.impl.plugin.PRESENTATION_LOG_TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class StateHandlerImpl<S : ViewState>(
    private val owner: String,
    private val logger: PresentationLogger?,
    initial: S,
) : StateHandler<S> {
    private val mutableState = MutableStateFlow(initial)

    override val state: StateFlow<S> = mutableState.asStateFlow()
    override val currentState: S get() = mutableState.value

    override fun setState(reducer: S.() -> S) {
        mutableState.update { old ->
            old.reducer().also { new ->
                logger?.log(tag = PRESENTATION_LOG_TAG, message = "$owner state · $old → $new", error = null)
            }
        }
    }
}
