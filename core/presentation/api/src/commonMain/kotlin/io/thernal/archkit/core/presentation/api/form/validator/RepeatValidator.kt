package io.thernal.archkit.core.presentation.api.form.validator

import io.thernal.archkit.core.presentation.api.string.UiString

/** Matches another field's value — "repeat password"; [original] reads it at validation time. */
class RepeatValidator<T>(
    private val original: () -> T,
    private val message: UiString = validationMessage(key = "validation.repeat", fallback = "Does not match"),
    private val matches: (T, T) -> Boolean = { value, other -> value == other },
) : FieldValidator<T> {
    override fun validate(value: T): UiString? {
        return message.takeUnless { matches(value, original()) }
    }
}
