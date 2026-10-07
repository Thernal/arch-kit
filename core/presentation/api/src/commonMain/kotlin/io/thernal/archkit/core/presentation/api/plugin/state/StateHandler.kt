package io.thernal.archkit.core.presentation.api.plugin.state

import kotlinx.coroutines.flow.StateFlow

interface StateHandler<S : ViewState> {
    val state: StateFlow<S>
    val currentState: S

    fun setState(reducer: S.() -> S)
}
