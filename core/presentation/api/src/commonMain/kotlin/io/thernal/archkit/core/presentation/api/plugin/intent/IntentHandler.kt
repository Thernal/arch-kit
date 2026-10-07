package io.thernal.archkit.core.presentation.api.plugin.intent

interface IntentHandler<I : ViewIntent> {
    fun postIntent(intent: I)
}
