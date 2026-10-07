package io.thernal.archkit.core.presentation.api.plugin

import kotlin.reflect.KClass

/** App-scoped lookup of every [PluginFactory]. */
interface PluginRegistry {
    fun <T : PluginFactory> factory(key: KClass<T>): T
}
