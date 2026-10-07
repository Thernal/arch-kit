package io.thernal.archkit.core.presentation.api.plugin.effect.message

import androidx.lifecycle.ViewModel
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectPlugin
import io.thernal.archkit.core.presentation.api.string.UiString
import io.thernal.archkit.core.presentation.api.string.toUiString

context(_: ViewModel, plugin: EffectPlugin<*>)
fun sendMessage(
    message: UiString,
    type: MessageType = MessageType.ERROR,
) {
    plugin.effectHandler.launchBaseEffect(MessageEffect(message = message, type = type))
}

context(_: ViewModel, plugin: EffectPlugin<*>)
fun sendMessage(
    failure: Failure,
    type: MessageType = MessageType.ERROR,
) {
    sendMessage(message = failure.toUiString(), type = type)
}
