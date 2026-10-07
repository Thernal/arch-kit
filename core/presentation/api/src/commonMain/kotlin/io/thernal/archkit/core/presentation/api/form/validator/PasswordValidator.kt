package io.thernal.archkit.core.presentation.api.form.validator

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.presentation.api.string.UiString

private const val PASSWORD_MIN_LENGTH = 8

/** At least eight characters, with a letter and a digit. */
class PasswordValidator(
    private val message: UiString = validationMessage(
        key = "validation.password",
        fallback = "At least 8 characters, with a letter and a digit",
    ),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        val text = value.text
        val isStrong = text.length >= PASSWORD_MIN_LENGTH && text.any(Char::isDigit) && text.any(Char::isLetter)
        return message.takeUnless { isStrong }
    }
}
