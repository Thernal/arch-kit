package io.thernal.archkit.core.presentation.impl.plugin.state

import io.thernal.archkit.core.presentation.api.plugin.PresentationLogger
import io.thernal.archkit.core.presentation.api.plugin.state.StateFactory
import io.thernal.archkit.core.presentation.api.plugin.state.StateHandler
import io.thernal.archkit.core.presentation.api.plugin.state.ViewState

class StateFactoryImpl(
    private val logger: PresentationLogger? = null,
) : StateFactory {
    override fun <S : ViewState> create(
        owner: String,
        log: Boolean,
        initializer: () -> S,
    ): StateHandler<S> {
        return StateHandlerImpl(owner = owner, logger = logger.takeIf { log }, initial = initializer())
    }
}
