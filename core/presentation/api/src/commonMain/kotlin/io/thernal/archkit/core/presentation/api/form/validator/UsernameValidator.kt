package io.thernal.archkit.core.presentation.api.form.validator

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.presentation.api.string.UiString

private val USERNAME = Regex("""^[A-Za-z0-9_]{3,32}$""")

class UsernameValidator(
    private val message: UiString = validationMessage(
        key = "validation.username",
        fallback = "3 to 32 letters, digits or _",
    ),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        return message.takeUnless { USERNAME.matches(value.text) }
    }
}
