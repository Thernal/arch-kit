package io.thernal.archkit.core.testing

import io.thernal.archkit.core.presentation.api.store.StoreFactory
import io.thernal.archkit.core.presentation.impl.store.factory.StoreFactoryImpl
import io.thernal.archkit.core.presentation.impl.store.settings.StoreSettings

/**
 * The real [StoreFactory], without DI: what a ViewModel under test takes as its `storeFactory`. A failure
 * that escapes a logic is thrown, so it fails the test; pass [onError] to collect them instead.
 */
fun testStoreFactory(onError: (Throwable) -> Unit = { error -> throw error }): StoreFactory {
    return StoreFactoryImpl(StoreSettings(onError = onError))
}
