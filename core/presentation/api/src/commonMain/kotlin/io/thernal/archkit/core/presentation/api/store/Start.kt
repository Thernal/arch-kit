package io.thernal.archkit.core.presentation.api.store

/** When a store runs its logic's `onStart`. */
enum class Start {
    /** When the store is created. */
    Eagerly,

    /**
     * On first use: the first intent, or the first collector of the state or of the effects. A store that
     * is created but never shown does no work. The default, as in Orbit.
     */
    Lazily,

    /** Only when `StoreHost.start` is called. For tests that check the state before the logic starts. */
    Manually,
}
