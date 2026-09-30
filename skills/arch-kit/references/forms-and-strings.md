# ContentState, strings, forms

## ContentState

`Idle` → `Loading(mock)` → `Success(data)` | `Error(failure)`. `reload()` keeps what is shown while loading;
`dataOrNull()`, `map { }`, `isLoading`…, `Result.toContentState()`.

```kotlin
when (val posts = state.posts) {
    ContentState.Idle, is ContentState.Loading -> Shimmer(posts.dataOrNull())
    is ContentState.Success -> PostList(posts.data)
    is ContentState.Error -> ErrorView(posts.failure.toUiString().resolve(), onRetry)
}
```

## UiString

`"Saved".ui()`, `Res.string.saved.ui()`, `UiString.Localized("checkout.empty", fallback = "Your cart is empty".ui())`,
`UiString.Composite(listOf(a, b), " · ")`. In Compose `text.resolve()`; outside (a snackbar from an effect
handler) `text.load(lookup)`. `failure.toUiString()` → key `failure.<status error>`; `fieldError.toUiString()`
→ `field_error.<error>`; both with fallbacks.

## Forms

```kotlin
data class SignUpState(
    val email: TextFormField = textFormField(validator = RequiredStringValidator() + EmailValidator()),
    val password: TextFormField = textFormField(validator = PasswordValidator()),
    val repeat: TextFormField = textFormField(),
) : FormState { override val fields get() = listOf(email, password, repeat) }
```

| Step | Call |
|---|---|
| typing | `email.edit(value, TrimStartTransformer, MaxLengthTransformer(64))` — clears the field's error |
| submit | `validate({ copy(email = email.validate(), …) }, passwordsMatch)`, then `if (state.isValid)` |
| cross-field rule | `FormRule<SignUpState> { form -> if (…) form else form.copy(repeat = form.repeat.withError(msg)) }` |
| server rejected | `state.withServerErrors(failure.errors, mapOf("email" to { copy(email = email.withError(it)) }))` → form + unbound errors |

Validators: `RequiredValidator`, `RequiredStringValidator`, `MinLengthValidator(min)`, `EmailValidator`,
`PasswordValidator` (8+, letter and digit), `UsernameValidator`, `RepeatValidator(original)`; each takes a
custom `UiString`. Keys: `validation.required`, `.email`, `.password`, `.min_length`, `.username`, `.repeat`.
