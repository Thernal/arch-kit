package io.thernal.archkit.core.presentation.api.store.keeper

import kotlinx.coroutines.flow.Flow

/**
 * What of a store's state survives the process being killed — Decompose's `StateKeeper`, on Jetpack's
 * `SavedStateHandle`, with kotlinx-serialization so that it works on every platform.
 *
 * Only what the user would be upset to lose is kept: a draft, a selection, a step. Loaded data is
 * fetched again, and a phase such as "publishing" is never restored — the work behind it is gone. That
 * is why a keeper saves a part ([save]) and merges it back into the initial state ([restore]), instead
 * of saving the whole state.
 *
 * ```kotlin
 * override val store = store(storeFactory, CreatePostState(), logic, keeper = handle.keeper("draft", SavedDraft.serializer(), …))
 * ```
 */
class StateKeeper<S> internal constructor(
    private val onRestore: (initial: S) -> S,
    private val onSave: suspend (states: Flow<S>) -> Unit,
) {
    /** [initial] with what was saved before the process died, merged in. For a host, once, at creation. */
    fun restore(initial: S): S {
        return onRestore(initial)
    }

    /** Saves the part of [states] the keeper keeps, until cancelled. For a host, in the store's scope. */
    suspend fun save(states: Flow<S>) {
        onSave(states)
    }
}
