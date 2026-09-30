package io.thernal.archkit.arch.testing

import io.thernal.archkit.arch.event.api.domain.EventBus
import io.thernal.archkit.arch.event.impl.domain.SharedFlowEventBus
import io.thernal.archkit.arch.presentation.api.presentation.effect.EffectPlugin
import io.thernal.archkit.arch.presentation.api.presentation.model.BaseEffect
import io.thernal.archkit.arch.presentation.api.presentation.plugin.PluginRegistry
import io.thernal.archkit.arch.presentation.impl.presentation.handler.DefaultPluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The real plugin factories, without DI: what a ViewModel under test takes as its `plugins`. */
fun testPlugins(eventBus: EventBus = SharedFlowEventBus()): PluginRegistry {
    return DefaultPluginRegistry.of(eventBus = eventBus)
}

/**
 * `runTest` for ViewModel code: `Dispatchers.Main` — which `viewModelScope` runs on — is the test's
 * own dispatcher for the duration, so intents, state and effects settle under the test clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(body: suspend TestScope.() -> Unit) {
    val dispatcher = UnconfinedTestDispatcher(StandardTestDispatcher().scheduler)
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) { body() }
    } finally {
        Dispatchers.resetMain()
    }
}

/** Every effect the ViewModel sends from now on, collected in [scope] — read the list after acting. */
fun EffectPlugin<*>.recordEffects(scope: CoroutineScope): List<BaseEffect> {
    val recorded = mutableListOf<BaseEffect>()
    effectHandler.effects.onEach { recorded += it }.launchIn(scope)
    return recorded
}
