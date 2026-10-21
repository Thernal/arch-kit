package io.thernal.archkit.core.presentation.impl.sample

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.failure.FieldError
import io.thernal.archkit.core.domain.failure.ResponseStatus
import io.thernal.archkit.core.domain.safecall.safeCall
import io.thernal.archkit.core.event.api.domain.BaseEvent
import io.thernal.archkit.core.event.api.domain.EventBus
import io.thernal.archkit.core.event.api.domain.observe
import io.thernal.archkit.core.event.impl.domain.SharedFlowEventBus
import io.thernal.archkit.core.presentation.api.content.ContentState
import io.thernal.archkit.core.presentation.api.content.reload
import io.thernal.archkit.core.presentation.api.form.field.FormField
import io.thernal.archkit.core.presentation.api.form.field.TextFormField
import io.thernal.archkit.core.presentation.api.form.field.textFormField
import io.thernal.archkit.core.presentation.api.form.state.FormRule
import io.thernal.archkit.core.presentation.api.form.state.FormState
import io.thernal.archkit.core.presentation.api.form.state.validate
import io.thernal.archkit.core.presentation.api.form.state.withServerErrors
import io.thernal.archkit.core.presentation.api.form.validator.FieldValidator
import io.thernal.archkit.core.presentation.api.form.validator.MinLengthValidator
import io.thernal.archkit.core.presentation.api.form.validator.RequiredStringValidator
import io.thernal.archkit.core.presentation.api.form.validator.plus
import io.thernal.archkit.core.presentation.api.logic.Logic
import io.thernal.archkit.core.presentation.api.store.Store
import io.thernal.archkit.core.presentation.api.store.StoreFactory
import io.thernal.archkit.core.presentation.api.store.StoreOwner
import io.thernal.archkit.core.presentation.api.store.scope.StoreScope
import io.thernal.archkit.core.presentation.api.store.scope.launch
import io.thernal.archkit.core.presentation.api.store.scope.observe
import io.thernal.archkit.core.presentation.api.store.scope.setState
import io.thernal.archkit.core.presentation.api.store.store
import io.thernal.archkit.core.presentation.api.store.settings.StoreSettings
import io.thernal.archkit.core.presentation.api.string.UiString
import io.thernal.archkit.core.presentation.api.string.toUiString
import io.thernal.archkit.core.presentation.api.viewmodel.coroutine.launch
import io.thernal.archkit.core.testing.runViewModelTest
import io.thernal.archkit.core.testing.storeTest
import io.thernal.archkit.core.testing.testStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** What the app's navigator would be: a feature's own effect carries a lambda over it. */
private class Router {
    val pushed = mutableListOf<String>()

    fun push(route: String) {
        pushed += route
    }
}

private data object ProfileSaved : BaseEvent

// A product's rule is the app's own validator: the kit ships none for an e-mail, a password or a username.
private val emailValidator = FieldValidator<TextFieldValue> { value ->
    UiString.Dynamic("Enter a valid email").takeUnless { '@' in value.text }
}

private data class SignUpState(
    val email: TextFormField = textFormField(validator = RequiredStringValidator() + emailValidator),
    val password: TextFormField = textFormField(validator = MinLengthValidator(min = 8)),
    val repeat: TextFormField = textFormField(),
    val profile: ContentState<String> = ContentState.Idle,
    val savedElsewhere: Int = 0,
) : FormState {
    override val fields: List<FormField<*>> get() = listOf(email, password, repeat)
}

private sealed interface SignUpIntent {
    data class EmailChanged(
        val value: String,
    ) : SignUpIntent

    data class PasswordChanged(
        val value: String,
        val repeat: String,
    ) : SignUpIntent

    data object Submit : SignUpIntent

    data object Crash : SignUpIntent
}

/** The feature's own effects, and the two every feature declares for itself: a message, a navigation. */
private sealed interface SignUpEffect {
    data object Celebrate : SignUpEffect

    data class Message(
        val message: UiString,
    ) : SignUpEffect

    data class Navigation(
        val process: Router.() -> Unit,
    ) : SignUpEffect
}

/** Cross-field: the repeat must match — what a field validator alone cannot see. */
private val passwordsMatch = FormRule<SignUpState> { form ->
    if (form.password.value.text == form.repeat.value.text) {
        form
    } else {
        form.copy(repeat = form.repeat.withError(UiString.Dynamic("Passwords differ")))
    }
}

private class SignUpLogic(
    private val bus: EventBus,
    private val signUp: suspend (String) -> Unit,
) : Logic<SignUpState, SignUpIntent, SignUpEffect> {
    private typealias Scope = StoreScope<SignUpState, SignUpEffect>

    override fun Scope.onStart() {
        observe(bus.observe<ProfileSaved>()) { setState { copy(savedElsewhere = savedElsewhere + 1) } }
    }

    override fun Scope.onIntent(intent: SignUpIntent) {
        when (intent) {
            is SignUpIntent.EmailChanged -> setState { copy(email = email.update(TextFieldValue(intent.value))) }

            is SignUpIntent.PasswordChanged -> setState {
                copy(
                    password = password.update(TextFieldValue(intent.value)),
                    repeat = repeat.update(TextFieldValue(intent.repeat)),
                )
            }

            SignUpIntent.Submit -> submit()

            SignUpIntent.Crash -> error("unexpected")
        }
    }

    private fun Scope.submit() {
        setState {
            validate(
                { copy(email = email.validate(), password = password.validate(), repeat = repeat.validate()) },
                passwordsMatch,
            )
        }
        if (!state.isValid) {
            return
        }
        launch {
            setState { copy(profile = profile.reload()) }
            safeCall { signUp(state.email.value.text) }
                .onSuccess {
                    setState { copy(profile = ContentState.Success(state.email.value.text)) }
                    emit(SignUpEffect.Celebrate)
                    bus.fire(ProfileSaved)
                    emit(SignUpEffect.Navigation { push("home") })
                }.onFailure { failure ->
                    val (form, _) = state.withServerErrors(
                        errors = (failure as Failure).errors,
                        bindings = mapOf("email" to { message -> copy(email = email.withError(message)) }),
                    )
                    setState { form.copy(profile = ContentState.Error(failure)) }
                    emit(SignUpEffect.Message(failure.toUiString()))
                }
        }
    }
}

private class SignUpViewModel(
    storeFactory: StoreFactory,
    bus: EventBus,
    signUp: suspend (String) -> Unit,
) : ViewModel(),
    StoreOwner<SignUpState, SignUpIntent, SignUpEffect> {
    override val store: Store<SignUpState, SignUpIntent, SignUpEffect> = store(
        factory = storeFactory,
        initialState = SignUpState(),
        logic = SignUpLogic(bus = bus, signUp = signUp),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class SampleFeatureTest {
    private fun typedIn(repeat: String = "abcd1234"): Array<SignUpIntent> {
        return arrayOf(
            SignUpIntent.EmailChanged("ada@example.com"),
            SignUpIntent.PasswordChanged("abcd1234", repeat),
        )
    }

    @Test
    fun `field and form rules block an invalid submit`() = runTest {
        var calls = 0
        storeTest(SignUpState(), SignUpLogic(SharedFlowEventBus()) { calls++ }) {
            send(SignUpIntent.EmailChanged("not-an-email"), SignUpIntent.PasswordChanged("abcd1234", "abcd12345"))
            skipAll()
            send(SignUpIntent.Submit)

            val form = nextState()
            assertNotNull(form.email.error)
            assertNull(form.password.error)
            assertEquals(expected = UiString.Dynamic("Passwords differ"), actual = form.repeat.error)
            assertFalse(form.isValid)
            assertEquals(expected = 0, actual = calls)
        }
    }

    @Test
    fun `a valid submit updates state sends effects navigates and fires an event`() = runTest {
        val bus = SharedFlowEventBus()
        val factory = testStoreFactory()
        val store = factory.create(scope = backgroundScope, initialState = SignUpState(), logic = SignUpLogic(bus) { })
        val listener = factory.create(
            scope = backgroundScope,
            initialState = SignUpState(),
            logic = SignUpLogic(bus) { },
        )
        val effects = mutableListOf<SignUpEffect>()
        val collecting = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(collecting) { store.effects.toList(effects) }
        backgroundScope.launch(collecting) { listener.state.collect { } }

        typedIn().forEach(store::postIntent)
        store.postIntent(SignUpIntent.Submit)
        runCurrent()

        assertEquals(expected = ContentState.Success("ada@example.com"), actual = store.state.value.profile)
        assertEquals(expected = SignUpEffect.Celebrate, actual = effects.first())
        val router = Router()
        assertIs<SignUpEffect.Navigation>(effects.last()).process(router)
        assertEquals(expected = listOf("home"), actual = router.pushed)
        assertEquals(expected = 1, actual = listener.state.value.savedElsewhere)
    }

    @Test
    fun `server field errors land on their fields and the failure is a message`() = runTest {
        val rejected = Failure(
            errors = listOf(FieldError(field = "email", error = "TAKEN")),
            status = ResponseStatus(error = "VALIDATION"),
        )
        storeTest(SignUpState(), SignUpLogic(SharedFlowEventBus()) { throw rejected }) {
            typedIn().forEach { intent -> send(intent) }
            skipAll()
            send(SignUpIntent.Submit)

            assertIs<ContentState.Loading<String>>(nextState().profile)
            val failed = nextState()
            val emailError = assertIs<UiString.Localized>(failed.email.error)
            assertEquals(expected = "field_error.taken", actual = emailError.key)
            assertIs<ContentState.Error>(failed.profile)
            val message = assertIs<SignUpEffect.Message>(nextEffect())
            assertEquals(expected = "failure.validation", actual = (message.message as UiString.Localized).key)
        }
    }

    @Test
    fun `an intent that throws is reported and the store keeps working`() = runViewModelTest {
        val reported = mutableListOf<Throwable>()
        val viewModel = SignUpViewModel(
            storeFactory = testStoreFactory(StoreSettings(onError = { error -> reported += error })),
            bus = SharedFlowEventBus(),
            signUp = { },
        )

        viewModel.store.postIntent(SignUpIntent.Crash)
        viewModel.store.postIntent(SignUpIntent.EmailChanged("still@works.com"))

        assertEquals(expected = 1, actual = reported.size)
        assertEquals(expected = "still@works.com", actual = viewModel.store.state.value.email.value.text)
    }
}
