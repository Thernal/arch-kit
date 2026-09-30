package io.thernal.archkit.arch.presentation.api.presentation

import androidx.compose.ui.text.input.TextFieldValue
import io.thernal.archkit.arch.domain.failure.Failure
import io.thernal.archkit.arch.presentation.api.presentation.form.edit
import io.thernal.archkit.arch.presentation.api.presentation.form.plus
import io.thernal.archkit.arch.presentation.api.presentation.form.textFormField
import io.thernal.archkit.arch.presentation.api.presentation.form.transformer.FilterTransformer
import io.thernal.archkit.arch.presentation.api.presentation.form.transformer.MaxLengthTransformer
import io.thernal.archkit.arch.presentation.api.presentation.form.validator.EmailValidator
import io.thernal.archkit.arch.presentation.api.presentation.form.validator.MinLengthValidator
import io.thernal.archkit.arch.presentation.api.presentation.form.validator.PasswordValidator
import io.thernal.archkit.arch.presentation.api.presentation.form.validator.RepeatValidator
import io.thernal.archkit.arch.presentation.api.presentation.form.validator.RequiredStringValidator
import io.thernal.archkit.arch.presentation.api.presentation.model.ContentState
import io.thernal.archkit.arch.presentation.api.presentation.model.dataOrNull
import io.thernal.archkit.arch.presentation.api.presentation.model.map
import io.thernal.archkit.arch.presentation.api.presentation.model.reload
import io.thernal.archkit.arch.presentation.api.presentation.model.toContentState
import io.thernal.archkit.arch.presentation.api.presentation.string.UiString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BuildingBlocksTest {
    @Test
    fun combinedValidatorsReportTheFirstFailure() {
        val validator = RequiredStringValidator() + EmailValidator()

        val required = assertIs<UiString.Localized>(validator.validate(TextFieldValue("")))
        val email = assertIs<UiString.Localized>(validator.validate(TextFieldValue("ada@")))

        assertEquals(expected = "validation.required", actual = required.key)
        assertEquals(expected = "validation.email", actual = email.key)
        assertNull(validator.validate(TextFieldValue("ada@example.com")))
    }

    @Test
    fun theBuiltInValidatorsAgreeWithTheirDocs() {
        assertNull(PasswordValidator().validate(TextFieldValue("abcd1234")))
        assertIs<UiString>(PasswordValidator().validate(TextFieldValue("abcdefgh")))
        assertIs<UiString>(MinLengthValidator(min = 3).validate(TextFieldValue(" ab ")))
        assertNull(RepeatValidator(original = { "x" }).validate("x"))
    }

    @Test
    fun transformersShapeAnEdit() {
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
    fun contentStateKeepsWhatIsShownWhileReloading() {
        val shown: ContentState<List<Int>> = ContentState.Success(listOf(1, 2))

        assertEquals(expected = listOf(1, 2), actual = shown.reload().dataOrNull())
        assertEquals(expected = ContentState.Success(2), actual = shown.map { it.size })
        assertIs<ContentState.Error>(Result.failure<Int>(IllegalStateException("x")).toContentState())
        assertEquals(expected = null, actual = ContentState.Error(Failure()).dataOrNull())
    }
}
