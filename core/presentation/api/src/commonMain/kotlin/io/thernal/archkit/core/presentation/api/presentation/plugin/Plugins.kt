package io.thernal.archkit.core.presentation.api.presentation.plugin

import kotlin.reflect.KClass

/**
 * The one member every plugin interface inherits, so a ViewModel overrides it once however many
 * plugins it implements. Each handler builder (`StateHandler { }`, `IntentHandler { }`, …) takes it as
 * a context parameter, which is why they resolve inside the ViewModel without a receiver.
 */
interface PluginContext {
    val plugins: PluginRegistry
}

/** Marker for a capability's factory (`StateFactory`, `IntentFactory`, …); contributed into the registry. */
interface PluginFactory

/** App-scoped lookup of every [PluginFactory]. */
interface PluginRegistry {
    fun <T : PluginFactory> factory(key: KClass<T>): T
}

inline fun <reified T : PluginFactory> PluginRegistry.factory(): T {
    return factory(T::class)
}

/**
 * Where the handlers log what flows through them — state changes, intents, effects, events — for a
 * debug console. Contributed by the app; none by default.
 */
fun interface PresentationLogger {
    fun log(
        tag: String,
        message: String,
        error: Throwable?,
    )
}
