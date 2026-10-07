package io.thernal.archkit.core.presentation.api.form.validator

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.presentation.api.string.UiString

class MinLengthValidator(
    private val min: Int,
    private val message: UiString = validationMessage(
        key = "validation.min_length",
        fallback = "At least $min characters",
    ),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        return message.takeIf { value.text.trim().length < min }
    }
}
