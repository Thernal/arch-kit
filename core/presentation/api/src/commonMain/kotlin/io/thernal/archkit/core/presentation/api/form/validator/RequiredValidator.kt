package io.thernal.archkit.core.presentation.api.form.validator

import io.thernal.archkit.core.presentation.api.string.UiString

class RequiredValidator<T>(
    private val message: UiString = validationMessage(key = "validation.required", fallback = "Required"),
) : FieldValidator<T?> {
    override fun validate(value: T?): UiString? {
        return message.takeIf { value == null }
    }
}
