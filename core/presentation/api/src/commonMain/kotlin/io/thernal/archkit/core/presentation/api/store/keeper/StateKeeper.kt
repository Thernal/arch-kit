package io.thernal.archkit.core.presentation.api.store.keeper

/**
 * What of a store's state survives the process being killed — Decompose's `StateKeeper`, on Jetpack's
 * `SavedStateHandle`, with kotlinx-serialization so that it works on every platform.
 *
 * Only what the user would be upset to lose is kept: a draft, a selection, a step. Loaded data is
 * fetched again, and a phase such as "publishing" is never restored — the work behind it is gone. That
 * is why a keeper saves a part and merges it back into the initial state, instead of saving the whole
 * state.
 *
 * A feature only passes one on: it comes from `StoreFactory.keeper` and goes to `store(…, keeper = …)`.
 * How it saves and restores is the host's business.
 *
 * ```kotlin
 * override val store = store(
 *     factory = storeFactory,
 *     initialState = CreatePostState(),
 *     logic = logic,
 *     keeper = storeFactory.keeper(handle, "draft", SavedDraft.serializer(), save = { … }, restore = { … }),
 * )
 * ```
 */
interface StateKeeper<S>

/** How often a keeper writes while the state keeps changing; the last value is never lost. */
const val DEFAULT_SAVE_INTERVAL_MILLIS = 300L
