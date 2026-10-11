package io.thernal.archkit.core.presentation.api.form.state

/**
 * A rule over the whole form — what one field's validator cannot see: "end after start", "at least one
 * contact". It returns the form with errors set on the fields it concerns.
 */
fun interface FormRule<S : FormState> {
    fun check(form: S): S
}
