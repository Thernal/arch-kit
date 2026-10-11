package io.thernal.archkit.core.testing

import io.thernal.archkit.core.presentation.api.store.StoreFactory
import io.thernal.archkit.core.presentation.impl.store.factory.StoreFactoryImpl
import io.thernal.archkit.core.presentation.api.store.settings.StoreSettings

/**
 * The real [StoreFactory], without DI: what a ViewModel under test takes as its `storeFactory`. By default a
 * failure that escapes a logic is thrown, so it fails the test; pass [settings] with an `onError` to collect them.
 */
fun testStoreFactory(settings: StoreSettings = StoreSettings()): StoreFactory {
    return StoreFactoryImpl(settings)
}
