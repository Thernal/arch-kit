package io.thernal.archkit.core.presentation.api.string

import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.failure.FieldError
import org.jetbrains.compose.resources.StringResource

fun String.ui(): UiString {
    return UiString.Dynamic(this)
}

fun StringResource.ui(vararg args: Any): UiString {
    return UiString.Resource(resource = this, args = args.toList())
}

/** `failure.<status error>` for the app to translate; the failure's message otherwise. */
fun Failure.toUiString(): UiString {
    val fallback = UiString.Localized(
        key = "failure.unknown",
        fallback = UiString.Dynamic(message ?: "Something went wrong"),
    )
    val error = status.error ?: return fallback
    return UiString.Localized(key = "failure.${error.lowercase()}", fallback = fallback)
}

/** `field_error.<error>` for the app to translate; the raw error otherwise. */
fun FieldError.toUiString(): UiString {
    return UiString.Localized(key = "field_error.${error.lowercase()}", fallback = UiString.Dynamic(error))
}
