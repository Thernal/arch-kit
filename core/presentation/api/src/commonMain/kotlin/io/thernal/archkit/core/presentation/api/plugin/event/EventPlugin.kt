package io.thernal.archkit.core.presentation.api.plugin.event

import io.thernal.archkit.core.presentation.api.plugin.PluginContext

interface EventPlugin : PluginContext {
    val eventHandler: EventHandler
}
