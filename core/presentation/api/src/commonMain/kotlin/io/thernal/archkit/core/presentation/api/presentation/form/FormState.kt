package io.thernal.archkit.core.presentation.api.presentation.form

import io.thernal.archkit.core.domain.failure.FieldError
import io.thernal.archkit.core.presentation.api.presentation.model.ViewState
import io.thernal.archkit.core.presentation.api.presentation.string.UiString
import io.thernal.archkit.core.presentation.api.presentation.string.toUiString

/** A state that is a form: its fields, valid when every one is. */
interface FormState : ViewState {
    val fields: List<FormField<*>>

    val isValid: Boolean get() = fields.all(FormField<*>::isValid)
}

/**
 * A rule over the whole form — what one field's validator cannot see: "end after start", "at least one
 * contact". It returns the form with errors set on the fields it concerns.
 */
fun interface FormRule<S : FormState> {
    fun check(form: S): S
}

/** Runs each field's validator (through [validateFields]), then every form [rules] in order. */
fun <S : FormState> S.validate(
    validateFields: S.() -> S,
    vararg rules: FormRule<S>,
): S {
    return rules.fold(validateFields()) { form, rule -> rule.check(form) }
}

/**
 * Puts a server's field errors on the form: [bindings] maps a server field name to how this form
 * shows an error on it. Errors for fields the form does not bind are returned second, for a message.
 */
fun <S : FormState> S.withServerErrors(
    errors: List<FieldError>,
    bindings: Map<String, S.(UiString) -> S>,
): Pair<S, List<FieldError>> {
    val unbound = mutableListOf<FieldError>()
    val form = errors.fold(this) { form, error ->
        val bind = bindings[error.field]
        if (bind == null) {
            unbound += error
            form
        } else {
            form.bind(error.toUiString())
        }
    }
    return form to unbound
}
