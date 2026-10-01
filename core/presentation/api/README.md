# core/presentation/api

The MVI runtime. Package `io.thernal.archkit.core.presentation.api.presentation`.

## Installing

| Module | Who depends on it |
|---|---|
| `:core:presentation:api` | every feature module with a ViewModel or a screen |
| `:core:presentation:impl` | the module that builds the graph |
| `:core:presentation:wiring` | the module that declares the Metro graph |
| `:core:testing` | test source sets |

### Dependencies you declare

Nothing is re-exported. A feature module with ViewModels declares, besides `:core:presentation:api`:
`:core:domain` (`Failure`, `safeCall`), `lifecycle-viewmodel` (`ViewModel`), `kotlinx-coroutines-core`, and —
for screens — Compose and `lifecycle-runtime-compose`; `compose-components-resources` when it uses
`UiString.Resource`; `:core:event:api` when it uses events.

### The graph and the root

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {                       // Metro's: the ViewModel maps and metroViewModelFactory
    val compositionLocals: Set<ProvidedValue<*>>
}

@Composable
fun AppRoot(graph: AppGraph) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
        ComponentLocals(graph.compositionLocals) {          // includes LocalViewModelFactoryRenderer
            val snackbar = remember { SnackbarHostState() }
            val lookup = LocalStringLookup.current
            val navigator = LocalNavigator.current           // your navigator, e.g. nav-kit's
            CompositionLocalProvider(
                LocalMessageEffectHandler provides MessageEffectHandler { snackbar.showSnackbar(it.message.load(lookup)) },
                LocalNavEffectHandler provides remember(navigator) { navEffectHandler(navigator) },
            ) { App() }
        }
    }
}
```

`PresentationWiring` needs an `EventBus` (`EventWiring`) and contributes the plugin registry.

### Navigation — the one-file bridge

The kit's `NavEffect<N>` is generic; name your navigator once:

```kotlin
// app/.../NavigationBridge.kt — with nav-kit's Navigator
context(_: ViewModel, plugin: EffectPlugin<*>)
fun navigation(command: Navigator.() -> Unit) {
    navigation<Navigator>(command)
}

@Suppress("UNCHECKED_CAST")
fun navEffectHandler(navigator: Navigator): NavEffectHandler {
    return NavEffectHandler { effect -> (effect as NavEffect<Navigator>).command(navigator) }
}
```

## A ViewModel

```kotlin
data class HomeState(val posts: ContentState<List<Post>> = ContentState.Idle) : ViewState

sealed interface HomeIntent : ViewIntent {
    data object Refresh : HomeIntent
    data class Open(val id: String) : HomeIntent
}

sealed interface HomeEffect : ViewEffect {
    data class Share(val url: String) : HomeEffect
}

@Inject
@ViewModelKey(HomeViewModel::class)
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())   // several supertypes: name the bound one
class HomeViewModel(
    override val plugins: PluginRegistry,
    private val loadPosts: LoadPostsUseCase,
) : ViewModel(), StatePlugin<HomeState>, IntentPlugin<HomeIntent>, EffectPlugin<HomeEffect> {
    override val stateHandler = StateHandler { HomeState() }
    override val effectHandler = EffectHandler<HomeEffect>()
    override val intentHandler = IntentHandler<HomeIntent> { intent ->
        when (intent) {
            HomeIntent.Refresh -> refresh()
            is HomeIntent.Open -> navigation { push(PostRoute(intent.id)) }
        }
    }

    private fun refresh() {
        launch {
            setState { copy(posts = posts.reload()) }
            safeCall { loadPosts(Unit) }                      // a failure becomes an error message
                .onSuccess { posts -> setState { copy(posts = ContentState.Success(posts)) } }
        }
    }
}
```

| Call (inside the ViewModel) | Does |
|---|---|
| `setState { copy(…) }`, `state` | replace / read the state |
| `postIntent(intent)` | from the screen: queue an intent; they run in order |
| `launchEffect(effect)` | a feature effect, delivered once |
| `sendMessage(UiString \| Failure, type)` | a `MessageEffect` |
| `navigation<N> { … }` | a `NavEffect` for the screen's navigator |
| `launch { }` | `viewModelScope.launch` that reports and shows an escaped exception |
| `safeCall { }` (effect plugin) | `safeCall` whose failure is shown as an error message |
| `useCase(params)` | a `FlowUseCase` as `Flow<Result<T>>` |

These compile only inside a ViewModel.

## The screen

```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel = createViewModel()) {
    val state by viewModel.collectAsState()
    val posts by viewModel.collectAsState { it.posts }          // recomposes only when posts change
    viewModel.OnEffectUpdate<HomeEffect> { effect ->
        when (effect) { is HomeEffect.Share -> share(effect.url) }
    }
    // viewModel.postIntent(HomeIntent.Refresh) …
}
```

`createViewModel()` already handles messages and navigation; call `OnEffectUpdate` with a collector for the
feature's own effects. `componentViewModel(key)` is the same for a ViewModel per component (a card), and
`createAssistedViewModel<T, Factory> { create(routeArg) }` for one with arguments DI cannot inject:

```kotlin
@AssistedInject
class PostViewModel(@Assisted val id: String, …) : ViewModel() {
    @dev.zacsweers.metro.AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class, binding = binding<ManualViewModelAssistedFactory>())
    fun interface Factory : AssistedFactory, ManualViewModelAssistedFactory {   // kit's marker + Metro's
        fun create(id: String): PostViewModel
    }
}
val viewModel = createAssistedViewModel<PostViewModel, PostViewModel.Factory> { create(route.id) }
```

## Events

```kotlin
class CartViewModel(override val plugins: PluginRegistry) : ViewModel(), EventPlugin {
    override val eventHandler = EventHandler()
    init { observeEvent<SessionExpired> { clear() } }
    fun checkout() { fireEvent(CartChanged) }
}
```

## ContentState

`Idle`, `Loading(mock)`, `Success(data)`, `Error(failure)`. `dataOrNull()`, `isLoading`/`isSuccess`/…,
`map { }`, `reload()` (loading, keeping what is shown), `Result.toContentState()`.

## UiString

`Dynamic("…")`, `Resource(Res.string.x, args)` (Compose Resources), `Localized(key, fallback)`,
`Composite(parts, separator)`. `"text".ui()`, `Res.string.x.ui(args)`. Resolve in Compose with
`uiString.resolve()`, outside it with `uiString.load(lookup)` (suspending). An app with key-based translations installs `LocalStringLookup`; the kit's validator
messages are keys `validation.required`, `validation.email`, `validation.password`, `validation.min_length`,
`validation.username`, `validation.repeat`, and failures resolve `failure.<status error>` /
`field_error.<error>`, each with an English fallback.

## Forms

```kotlin
data class SignUpState(
    val email: TextFormField = textFormField(validator = RequiredStringValidator() + EmailValidator()),
    val password: TextFormField = textFormField(validator = PasswordValidator()),
    val repeat: TextFormField = textFormField(),
) : FormState {
    override val fields get() = listOf(email, password, repeat)
}

val passwordsMatch = FormRule<SignUpState> { form ->
    if (form.password.value.text == form.repeat.value.text) form
    else form.copy(repeat = form.repeat.withError(UiString.Localized("validation.repeat", "Does not match".ui())))
}

// typing
setState { copy(email = email.edit(newValue, TrimStartTransformer, MaxLengthTransformer(64))) }
// submit
setState { validate({ copy(email = email.validate(), password = password.validate()) }, passwordsMatch) }
if (state.isValid) submit()
// the server rejected fields
val (form, unshown) = state.withServerErrors(failure.errors, mapOf("email" to { copy(email = email.withError(it)) }))
```

`FormField.update` clears the error (the user is fixing it); `validate` runs the validator; `withError` sets
one. Validators: `RequiredValidator`, `RequiredStringValidator`, `MinLengthValidator`, `EmailValidator`,
`PasswordValidator`, `UsernameValidator`, `RepeatValidator`; transformers: `MaxLengthTransformer`,
`TrimStartTransformer`, `FilterTransformer`.

## Logging

Contribute a `PresentationLogger` into the graph's set (`@Provides @IntoSet`): every state change, intent,
effect and event goes through it — a debug console, in non-production builds.

## Testing

```kotlin
@Test
fun refreshShowsPosts() = runViewModelTest {
    val viewModel = HomeViewModel(plugins = testPlugins(), loadPosts = FakeLoadPosts(posts))
    val effects = viewModel.recordEffects(backgroundScope)

    viewModel.postIntent(HomeIntent.Refresh)
    testScheduler.advanceUntilIdle()

    assertEquals(ContentState.Success(posts), viewModel.state.posts)
}
```

`testPlugins()` gives the real handlers without DI; `runViewModelTest` makes `Dispatchers.Main` the test's
dispatcher; `recordEffects` collects what the ViewModel sends.
