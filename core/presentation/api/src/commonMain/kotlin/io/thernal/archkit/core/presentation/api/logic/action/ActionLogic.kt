package io.thernal.archkit.core.presentation.api.logic.action

import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.state.Partial

/**
 * Level 2. An intent says what the user did; an action says what to do about it, with every parameter it
 * needs already gathered from the state. Processing then never reads a state that changed under it, and
 * the action, being plain data, is what a test asserts on.
 *
 * Every action runs under a [Policy] for its [key]. That is where races are decided — once, by name —
 * instead of by `if (isLoading) return` guards and hand-kept `Job`s. Latest also debounces for free: a
 * `delay` at the start of [process] is cancelled by the next keystroke.
 *
 * Results go back as [Partial]s, so the reduction is in one place and the processing is only IO.
 */
abstract class ActionLogic<S, I, A : Any, E> : Logic<S, I, E> {
    private val runner = ActionRunner()

    /**
     * Intent → action: read the state, gather the parameters, validate. Synchronous changes (a typed
     * character) are reduced here and need no action. Null when there is nothing to run.
     */
    protected abstract fun StoreScope<S, E>.toAction(intent: I): A?

    /** Actions that share a policy's key meet under it: `Policy.Latest("search")`, `Policy.Latest("follow:$id")`. */
    protected open fun policy(action: A): Policy = Policy.Parallel

    protected abstract suspend fun StoreScope<S, E>.process(action: A)

    final override fun StoreScope<S, E>.onIntent(intent: I) {
        toAction(intent)?.let { action -> dispatch(action) }
    }

    /** Runs [action] as if an intent had produced it — for work the logic starts itself (on start, on a timer). */
    protected fun StoreScope<S, E>.dispatch(action: A) {
        runner.run(scope = this, action = action, policy = policy(action)) { process(action) }
    }
}
