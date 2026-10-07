package io.thernal.archkit.core.presentation.impl

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import io.thernal.archkit.core.domain.failure.Failure
import io.thernal.archkit.core.domain.failure.FieldError
import io.thernal.archkit.core.domain.failure.ResponseStatus
import io.thernal.archkit.core.event.api.domain.BaseEvent
import io.thernal.archkit.core.event.impl.domain.SharedFlowEventBus
import io.thernal.archkit.core.presentation.api.content.ContentState
import io.thernal.archkit.core.presentation.api.content.reload
import io.thernal.archkit.core.presentation.api.form.FormField
import io.thernal.archkit.core.presentation.api.form.FormRule
import io.thernal.archkit.core.presentation.api.form.FormState
import io.thernal.archkit.core.presentation.api.form.TextFormField
import io.thernal.archkit.core.presentation.api.form.textFormField
import io.thernal.archkit.core.presentation.api.form.validate
import io.thernal.archkit.core.presentation.api.form.validator.EmailValidator
import io.thernal.archkit.core.presentation.api.form.validator.PasswordValidator
import io.thernal.archkit.core.presentation.api.form.validator.RequiredStringValidator
import io.thernal.archkit.core.presentation.api.form.validator.plus
import io.thernal.archkit.core.presentation.api.form.withServerErrors
import io.thernal.archkit.core.presentation.api.plugin.PluginRegistry
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectHandler
import io.thernal.archkit.core.presentation.api.plugin.effect.EffectPlugin
import io.thernal.archkit.core.presentation.api.plugin.effect.ViewEffect
import io.thernal.archkit.core.presentation.api.plugin.effect.launchEffect
import io.thernal.archkit.core.presentation.api.plugin.effect.message.MessageEffect
import io.thernal.archkit.core.presentation.api.plugin.effect.navigation.NavEffect
import io.thernal.archkit.core.presentation.api.plugin.effect.navigation.navigation
import io.thernal.archkit.core.presentation.api.plugin.event.EventHandler
import io.thernal.archkit.core.presentation.api.plugin.event.EventPlugin
import io.thernal.archkit.core.presentation.api.plugin.event.fireEvent
import io.thernal.archkit.core.presentation.api.plugin.event.observeEvent
import io.thernal.archkit.core.presentation.api.plugin.intent.IntentHandler
import io.thernal.archkit.core.presentation.api.plugin.intent.IntentPlugin
import io.thernal.archkit.core.presentation.api.plugin.intent.ViewIntent
import io.thernal.archkit.core.presentation.api.plugin.intent.postIntent
import io.thernal.archkit.core.presentation.api.plugin.state.StateHandler
import io.thernal.archkit.core.presentation.api.plugin.state.StatePlugin
import io.thernal.archkit.core.presentation.api.plugin.state.setState
import io.thernal.archkit.core.presentation.api.plugin.state.state
import io.thernal.archkit.core.presentation.api.string.UiString
import io.thernal.archkit.core.presentation.api.viewmodel.launch
import io.thernal.archkit.core.testing.recordEffects
import io.thernal.archkit.core.testing.runViewModelTest
import io.thernal.archkit.core.testing.testPlugins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Router {
    val pushed = mutableListOf<String>()

    fun push(route: String) {
        pushed += route
    }
}

private data object ProfileSaved : BaseEvent

private data class SignUpState(
    val email: TextFormField = textFormField(validator = RequiredStringValidator() + EmailValidator()),
    val password: TextFormField = textFormField(validator = PasswordValidator()),
    val repeat: TextFormField = textFormField(),
    val profile: ContentState<String> = ContentState.Idle,
    val savedElsewhere: Int = 0,
) : FormState {
    override val fields: List<FormField<*>> get() = listOf(email, password, repeat)
}

private sealed interface SignUpIntent : ViewIntent {
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

private sealed interface SignUpEffect : ViewEffect {
    data object Celebrate : SignUpEffect
}

/** Cross-field: the repeat must match — what a field validator alone cannot see. */
private val passwordsMatch = FormRule<SignUpState> { form ->
    if (form.password.value.text == form.repeat.value.text) {
        form
    } else {
        form.copy(repeat = form.repeat.withError(UiString.Dynamic("Passwords differ")))
    }
}

private class SignUpViewModel(
    override val plugins: PluginRegistry,
    private val signUp: suspend (String) -> Unit,
) : ViewModel(),
    StatePlugin<SignUpState>,
    IntentPlugin<SignUpIntent>,
    EffectPlugin<SignUpEffect>,
    EventPlugin {
    override val stateHandler: StateHandler<SignUpState> = StateHandler { SignUpState() }
    override val effectHandler: EffectHandler<SignUpEffect> = EffectHandler()
    override val eventHandler: EventHandler = EventHandler()
    override val intentHandler: IntentHandler<SignUpIntent> = IntentHandler { intent ->
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

    init {
        observeEvent<ProfileSaved> { setState { copy(savedElsewhere = savedElsewhere + 1) } }
    }

    private fun submit() {
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
                    launchEffect(SignUpEffect.Celebrate)
                    fireEvent(ProfileSaved)
                    navigation<Router> { push("home") }
                }.onFailure { failure ->
                    val (form, _) = state.withServerErrors(
                        errors = (failure as Failure).errors,
                        bindings = mapOf("email" to { message -> copy(email = email.withError(message)) }),
                    )
                    setState { form.copy(profile = ContentState.Error(failure)) }
                }
        }
    }
}

class SampleFeatureTest {
    @Test
    fun `field and form rules block an invalid submit`() {
        runViewModelTest {
            var calls = 0
            val viewModel = SignUpViewModel(testPlugins()) { calls++ }

            viewModel.postIntent(SignUpIntent.EmailChanged("not-an-email"))
            viewModel.postIntent(SignUpIntent.PasswordChanged("abcd1234", "abcd12345"))
            viewModel.postIntent(SignUpIntent.Submit)

            assertNotNull(viewModel.state.email.error)
            assertNull(viewModel.state.password.error)
            assertEquals(expected = UiString.Dynamic("Passwords differ"), actual = viewModel.state.repeat.error)
            assertFalse(viewModel.state.isValid)
            assertEquals(expected = 0, actual = calls)
        }
    }

    @Test
    fun `a valid submit updates state sends effects navigates and fires an event`() {
        runViewModelTest {
            val bus = SharedFlowEventBus()
            val viewModel = SignUpViewModel(testPlugins(bus)) { }
            val listener = SignUpViewModel(testPlugins(bus)) { }
            val effects = viewModel.recordEffects(backgroundScope)

            viewModel.postIntent(SignUpIntent.EmailChanged("ada@example.com"))
            viewModel.postIntent(SignUpIntent.PasswordChanged("abcd1234", "abcd1234"))
            viewModel.postIntent(SignUpIntent.Submit)
            testScheduler.advanceUntilIdle()

            assertEquals(expected = ContentState.Success("ada@example.com"), actual = viewModel.state.profile)
            assertEquals(expected = SignUpEffect.Celebrate, actual = effects.first())
            val router = Router()
            @Suppress("UNCHECKED_CAST")
            (effects.last() as NavEffect<Router>).command(router)
            assertEquals(expected = listOf("home"), actual = router.pushed)
            assertEquals(expected = 1, actual = listener.state.savedElsewhere)
        }
    }

    @Test
    fun `server field errors land on their fields and the failure is shown`() {
        runViewModelTest {
            val rejected = Failure(
                errors = listOf(FieldError(field = "email", error = "TAKEN")),
                status = ResponseStatus(error = "VALIDATION"),
            )
            val viewModel = SignUpViewModel(testPlugins()) { throw rejected }
            val effects = viewModel.recordEffects(backgroundScope)

            viewModel.postIntent(SignUpIntent.EmailChanged("ada@example.com"))
            viewModel.postIntent(SignUpIntent.PasswordChanged("abcd1234", "abcd1234"))
            viewModel.postIntent(SignUpIntent.Submit)
            testScheduler.advanceUntilIdle()

            val emailError = assertIs<UiString.Localized>(viewModel.state.email.error)
            assertEquals(expected = "field_error.taken", actual = emailError.key)
            assertIs<ContentState.Error>(viewModel.state.profile)
            val message = assertIs<MessageEffect>(effects.single())
            assertEquals(expected = "failure.validation", actual = (message.message as UiString.Localized).key)
        }
    }

    @Test
    fun `an intent that throws is shown and does not crash`() {
        runViewModelTest {
            val viewModel = SignUpViewModel(testPlugins()) { }
            val effects = viewModel.recordEffects(backgroundScope)

            viewModel.postIntent(SignUpIntent.Crash)
            viewModel.postIntent(SignUpIntent.EmailChanged("still@works.com"))

            assertIs<MessageEffect>(effects.single())
            assertTrue(viewModel.state.email.value.text == "still@works.com")
        }
    }
}
