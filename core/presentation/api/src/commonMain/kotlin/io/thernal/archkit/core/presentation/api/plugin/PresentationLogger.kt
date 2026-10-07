package io.thernal.archkit.core.presentation.api.plugin

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
