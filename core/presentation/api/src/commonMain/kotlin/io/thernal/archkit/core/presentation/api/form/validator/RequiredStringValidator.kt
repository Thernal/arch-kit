package io.thernal.archkit.core.presentation.api.form.validator

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.presentation.api.string.UiString

class RequiredStringValidator(
    private val message: UiString = validationMessage(key = "validation.required", fallback = "Required"),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        return message.takeIf { value.text.isBlank() }
    }
}
