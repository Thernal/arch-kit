package io.thernal.archkit.core.presentation.api

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.presentation.api.content.ContentState
import io.thernal.archkit.core.presentation.api.content.dataOrNull
import io.thernal.archkit.core.presentation.api.content.map
import io.thernal.archkit.core.presentation.api.content.reload
import io.thernal.archkit.core.presentation.api.content.toContentState
import io.thernal.archkit.core.presentation.api.form.field.edit
import io.thernal.archkit.core.presentation.api.form.field.textFormField
import io.thernal.archkit.core.presentation.api.form.state.validate
import io.thernal.archkit.core.presentation.api.form.transformer.FilterTransformer
import io.thernal.archkit.core.presentation.api.form.transformer.MaxLengthTransformer
import io.thernal.archkit.core.presentation.api.form.validator.EmailValidator
import io.thernal.archkit.core.presentation.api.form.validator.MinLengthValidator
import io.thernal.archkit.core.presentation.api.form.validator.PasswordValidator
import io.thernal.archkit.core.presentation.api.form.validator.RepeatValidator
import io.thernal.archkit.core.presentation.api.form.validator.RequiredStringValidator
import io.thernal.archkit.core.presentation.api.form.validator.plus
import io.thernal.archkit.core.presentation.api.string.UiString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BuildingBlocksTest {
    @Test
    fun `combined validators report the first failure`() {
        val validator = RequiredStringValidator() + EmailValidator()

        val required = assertIs<UiString.Localized>(validator.validate(TextFieldValue("")))
        val email = assertIs<UiString.Localized>(validator.validate(TextFieldValue("ada@")))

        assertEquals(expected = "validation.required", actual = required.key)
        assertEquals(expected = "validation.email", actual = email.key)
        assertNull(validator.validate(TextFieldValue("ada@example.com")))
    }

    @Test
    fun `the built-in validators agree with their docs`() {
        assertNull(PasswordValidator().validate(TextFieldValue("abcd1234")))
        assertIs<UiString>(PasswordValidator().validate(TextFieldValue("abcdefgh")))
        assertIs<UiString>(MinLengthValidator(min = 3).validate(TextFieldValue(" ab ")))
        assertNull(RepeatValidator(original = { "x" }).validate("x"))
    }

    @Test
    fun `transformers shape an edit`() {
        val code = textFormField("12")

        val edited = code.edit(
            TextFieldValue("123a"),
            FilterTransformer(Char::isDigit),
            MaxLengthTransformer(maxLength = 4),
        )
        val longer = code.edit(TextFieldValue("12345"), MaxLengthTransformer(maxLength = 4))

        assertEquals(expected = "12", actual = edited.value.text)
        assertEquals(expected = "12", actual = longer.value.text)
    }

    @Test
    fun `ContentState keeps what is shown while reloading`() {
        val shown: ContentState<List<Int>> = ContentState.Success(listOf(1, 2))

        assertEquals(expected = listOf(1, 2), actual = shown.reload().dataOrNull())
        assertEquals(expected = ContentState.Success(2), actual = shown.map { it.size })
        assertIs<ContentState.Error>(Result.failure<Int>(IllegalStateException("x")).toContentState())
        assertEquals(expected = null, actual = ContentState.Error(Failure()).dataOrNull())
    }
}
