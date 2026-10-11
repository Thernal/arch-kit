package io.thernal.archkit.core.testing

import io.thernal.archkit.core.presentation.api.store.StoreFactory
import io.thernal.archkit.core.presentation.api.store.StoreSettings
import io.thernal.archkit.core.presentation.impl.store.StoreFactoryImpl

/** The real [StoreFactory], without DI: what a ViewModel under test takes as its `storeFactory`. Fails fast by default. */
fun testStoreFactory(settings: StoreSettings = StoreSettings()): StoreFactory {
    return StoreFactoryImpl(settings)
}
