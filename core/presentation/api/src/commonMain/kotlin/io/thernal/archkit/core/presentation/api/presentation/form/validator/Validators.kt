package io.thernal.archkit.core.presentation.api.presentation.form.validator

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.presentation.api.presentation.form.FieldValidator
import io.thernal.archkit.core.presentation.api.presentation.string.UiString

// Every message is a key the app can translate (StringLookup), with an English fallback.
private fun message(
    key: String,
    fallback: String,
): UiString {
    return UiString.Localized(key = key, fallback = UiString.Dynamic(fallback))
}

private const val PASSWORD_MIN_LENGTH = 8

private val EMAIL = Regex(pattern = """^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$""", option = RegexOption.IGNORE_CASE)
private val USERNAME = Regex("""^[A-Za-z0-9_]{3,32}$""")

class RequiredValidator<T>(
    private val message: UiString = message(key = "validation.required", fallback = "Required"),
) : FieldValidator<T?> {
    override fun validate(value: T?): UiString? {
        return message.takeIf { value == null }
    }
}

class RequiredStringValidator(
    private val message: UiString = message(key = "validation.required", fallback = "Required"),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        return message.takeIf { value.text.isBlank() }
    }
}

class MinLengthValidator(
    private val min: Int,
    private val message: UiString = message(key = "validation.min_length", fallback = "At least $min characters"),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        return message.takeIf { value.text.trim().length < min }
    }
}

class EmailValidator(
    private val message: UiString = message(key = "validation.email", fallback = "Enter a valid email"),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        return message.takeUnless { EMAIL.matches(value.text.trim()) }
    }
}

/** At least eight characters, with a letter and a digit. */
class PasswordValidator(
    private val message: UiString = message(
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

class UsernameValidator(
    private val message: UiString = message(key = "validation.username", fallback = "3 to 32 letters, digits or _"),
) : FieldValidator<TextFieldValue> {
    override fun validate(value: TextFieldValue): UiString? {
        return message.takeUnless { USERNAME.matches(value.text) }
    }
}

/** Matches another field's value — "repeat password"; [original] reads it at validation time. */
class RepeatValidator<T>(
    private val original: () -> T,
    private val message: UiString = message(key = "validation.repeat", fallback = "Does not match"),
    private val matches: (T, T) -> Boolean = { value, other -> value == other },
) : FieldValidator<T> {
    override fun validate(value: T): UiString? {
        return message.takeUnless { matches(value, original()) }
    }
}
