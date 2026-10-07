package io.thernal.archkit.core.presentation.api.plugin

/**
 * The one member every plugin interface inherits, so a ViewModel overrides it once however many
 * plugins it implements. Each handler builder (`StateHandler { }`, `IntentHandler { }`, …) takes it as
 * a context parameter, which is why they resolve inside the ViewModel without a receiver.
 */
interface PluginContext {
    val plugins: PluginRegistry
}
