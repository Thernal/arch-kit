package io.thernal.archkit.core.presentation.api.form.state

import io.thernal.archkit.core.domain.failure.FieldError
import io.thernal.archkit.core.presentation.api.string.UiString
import io.thernal.archkit.core.presentation.api.string.toUiString

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
