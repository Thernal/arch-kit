package io.thernal.archkit.core.presentation.api.plugin

inline fun <reified T : PluginFactory> PluginRegistry.factory(): T {
    return factory(T::class)
}
