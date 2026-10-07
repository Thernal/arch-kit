package io.thernal.archkit.core.presentation.api.plugin.event

import io.thernal.archkit.core.presentation.api.plugin.PluginFactory
import kotlinx.coroutines.CoroutineScope

interface EventFactory : PluginFactory {
    fun create(
        owner: String,
        scope: CoroutineScope,
    ): EventHandler
}
