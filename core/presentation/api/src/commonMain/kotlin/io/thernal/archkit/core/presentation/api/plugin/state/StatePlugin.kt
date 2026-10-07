package io.thernal.archkit.core.presentation.api.plugin.state

import io.thernal.archkit.core.presentation.api.plugin.PluginContext

interface StatePlugin<S : ViewState> : PluginContext {
    val stateHandler: StateHandler<S>
}
