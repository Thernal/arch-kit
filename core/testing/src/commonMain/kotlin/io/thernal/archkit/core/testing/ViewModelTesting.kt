package io.thernal.archkit.core.testing

import io.thernal.archkit.core.presentation.api.logic.Logic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * `runTest` for code that runs on a ViewModel's scope: `Dispatchers.Main` — which `viewModelScope` runs
 * on — is the test's own dispatcher for the duration, so intents, state and effects settle under the
 * test clock. A [Logic] alone is tested with `storeTest`, which needs no ViewModel.
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
