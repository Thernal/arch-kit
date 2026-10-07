package io.thernal.archkit.core.presentation.api.plugin.intent

import io.thernal.archkit.core.presentation.api.plugin.PluginContext

interface IntentPlugin<I : ViewIntent> : PluginContext {
    val intentHandler: IntentHandler<I>
}
