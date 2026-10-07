package io.thernal.archkit.core.presentation.api.plugin.state

import io.thernal.archkit.core.presentation.api.plugin.PluginFactory

interface StateFactory : PluginFactory {
    fun <S : ViewState> create(
        owner: String,
        log: Boolean,
        initializer: () -> S,
    ): StateHandler<S>
}
