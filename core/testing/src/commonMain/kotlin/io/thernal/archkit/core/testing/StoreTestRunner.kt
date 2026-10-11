package io.thernal.archkit.core.testing

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.keeper.StateKeeper
import kotlinx.coroutines.test.TestScope

/**
 * Tests a [Logic] the way Orbit tests a container: every state change and every effect is recorded in
 * order, and the test takes them one by one. A state is asserted as the change from the one before
 * (`expectState { copy(isLoading = true) }`), so a test reads like the reducer it checks, and a change
 * the test did not expect fails it: when the block ends, nothing may be left unconsumed, and every
 * failure the store reported must have been expected.
 *
 * Time is the test's: [advance] moves it, and [send] runs everything that is due. Nothing is collected
 * unless the test asks — [subscribe] is a screen being visible.
 *
 * ```kotlin
 * @Test fun search() = runTest {
 *     storeTest(SearchState(), SearchLogic(users)) {
 *         send(SearchIntent.EditQuery("ada"))
 *         expectState { copy(query = "ada") }
 *         advance(1_000)
 *         expectState { copy(results = ContentState.Loading()) }
 *     }
 * }
 * ```
 */
fun <S, I, E> TestScope.storeTest(
    initialState: S,
    logic: Logic<S, I, E>,
    keeper: StateKeeper<S>? = null,
    start: Boolean = true,
    block: StoreTest<S, I, E>.() -> Unit,
) {
    val test = StoreTest(scope = this, initialState = initialState, logic = logic, keeper = keeper)
    if (start) {
        test.start()
    }
    test.block()
    test.finish()
}
