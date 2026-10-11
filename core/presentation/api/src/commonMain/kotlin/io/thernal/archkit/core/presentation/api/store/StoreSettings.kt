package io.thernal.archkit.core.presentation.api.store

/**
 * How a store runs, as opposed to what it does — the part an app sets once, for every store, through
 * its base ViewModel or its DI.
 */
class StoreSettings(
    val start: Start = Start.Lazily,
    /**
     * Gets every failure: in an intent, and in work the logic launched. The default fails fast — it
     * throws, so a bug crashes in development and in tests. An app sets one that reports instead, and the
     * store then keeps working.
     */
    val onError: (Throwable) -> Unit = { error -> throw error },
    /** Every observer sees everything, in the order of the list. One that fails is reported to [onError]. */
    val observers: List<StoreObserver> = emptyList(),
)
