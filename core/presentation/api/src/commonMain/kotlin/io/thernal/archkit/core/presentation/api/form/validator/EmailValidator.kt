package io.thernal.archkit.core.presentation.api.form.validator

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.presentation.api.string.UiString

private val EMAIL = Regex(pattern = """^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$""", option = RegexOption.IGNORE_CASE)

class EmailValidator(
    private val message: UiString = validationMessage(key = "validation.email", fallback = "Enter a valid email"),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        return message.takeUnless { EMAIL.matches(value.text.trim()) }
    }
}
