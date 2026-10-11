# core/presentation/api

The MVI runtime. Package `io.thernal.archkit.core.presentation.api` — the module is the presentation layer, so no `presentation` package repeats it.

A screen has a **state**, **intents** and **effects**, and one `Logic` class that turns intents into states and
effects. The ViewModel only owns the `Store` that hosts the logic. This page is the kit's API, task by task;
the architecture itself — levels, rules, cookbook, migrations — is in [`docs/mvi/`](../../../docs/mvi/README.md).

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
`UiString.Resource`; `:core:event:api` when it uses events; `lifecycle-viewmodel-savedstate` and
`kotlinx-serialization-core` when it keeps a draft (`StateKeeper`).

### The graph and the root

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {                       // Metro's: the ViewModel maps and metroViewModelFactory
    val compositionLocals: Set<ProvidedValue<*>>
}

@Composable
fun AppRoot(graph: AppGraph) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
        ContributedLocals(graph.compositionLocals) {          // includes LocalViewModelResolver
            App()
        }
    }
}
```

`PresentationProvidersModule` contributes the `StoreFactory` every ViewModel takes. Failures that escape a
logic go to `FailureReporting`; contribute a `StoreObserver` into the graph's set (`@Provides @IntoSet`) to see
every intent, state change and effect — a debug console, analytics.

The kit installs nothing for messages or navigation. A snackbar host and a navigator are the screen's, or the
app's own composition locals: see [Messages and navigation](#messages-and-navigation).

## A feature

```kotlin
data class HomeState(val posts: ContentState<List<Post>> = ContentState.Idle)

sealed interface HomeIntent {
    data object Refresh : HomeIntent
    data class Open(val id: String) : HomeIntent
}

sealed interface HomeEffect {
    data class Share(val url: String) : HomeEffect
    data class Message(val message: UiString) : HomeEffect                // the feature's own, see below
    data class Navigation(val process: Navigator.() -> Unit) : HomeEffect
}

private typealias Scope = StoreScope<HomeState, HomeEffect>

class HomeLogic(private val loadPosts: LoadPostsUseCase) : Logic<HomeState, HomeIntent, HomeEffect> {
    override fun Scope.onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Refresh -> refresh()
            is HomeIntent.Open -> emit(HomeEffect.Navigation { push(PostRoute(intent.id)) })
        }
    }

    private fun Scope.refresh() {
        launch {
            setState { copy(posts = posts.reload()) }
            safeCall { loadPosts(Unit) }
                .onSuccess { posts -> setState { copy(posts = ContentState.Success(posts)) } }
                .onFailure { failure -> emit(HomeEffect.Message(failure.toUiString())) }
        }
    }
}

@Inject
@ViewModelKey(HomeViewModel::class)
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())   // several supertypes: name the bound one
class HomeViewModel(
    storeFactory: StoreFactory,
    loadPosts: LoadPostsUseCase,
) : ViewModel(), StoreOwner<HomeState, HomeIntent, HomeEffect> {
    override val store = store(storeFactory, HomeState(), HomeLogic(loadPosts))
}
```

| Call (inside a `Logic`, on `StoreScope`) | Does |
|---|---|
| `setState { copy(…) }`, `state` | replace / read the state |
| `emit(effect)` | an effect, delivered once to one collector |
| `launch { }` | work that dies with the store; the store is busy while it runs (`Store.isBusy`) |
| `observe(flow) { }` | a source outside the screen, collected while the store lives — a session, an event bus |
| `whileSubscribed { }` | work that runs only while the screen is visible |
| `safeCall { }` (domain) | a `Result` with a `Failure`, reported; cancellation is rethrown |
| `useCase(params)` | a `FlowUseCase` as `Flow<Result<T>>` |

A `Logic` is a plain class: no ViewModel, no Android, no Compose. It is tested alone, and it can be hosted as
a `Slice` of a bigger screen or a `Slot` (a dialog) unchanged. A screen that outgrows `setState`-and-`launch`
moves up the levels — `ActionLogic` with keyed `Policy`s for overlapping work, phases with `whileIn`/`runOn`
for exclusive modes, `Slice` for parts that change for different reasons — and the screen never notices.
[`docs/mvi/guide.md`](../../../docs/mvi/guide.md) says when.

## The screen

```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel = createViewModel()) {
    val store = viewModel.store
    val state by store.collectAsState()
    val posts by state.select { it.posts }                      // recomposes only when posts change
    store.CollectEffects { effect ->
        when (effect) {
            is HomeEffect.Share -> share(effect.url)
            is HomeEffect.Message -> snackbar.showSnackbar(effect.message.load(lookup))
            is HomeEffect.Navigation -> effect.process(navigator)
        }
    }
    // store.postIntent(HomeIntent.Refresh) …
}
```

`CollectEffects` hands each effect to the block once, while the screen is `RESUMED` (`minActiveState` changes
that): an effect emitted while the screen is in the background, or while nothing collects, waits in the store
and is handled when the screen is back — a screen that is not visible does not navigate or show a message.
The block runs in a `CoroutineScope`: suspending in it (`showSnackbar`) holds the next effect back, `launch`
lets it through. Collected on `Dispatchers.Main.immediate`. Only one collector at a time: a second fails at
once. Without a composition, `store.collectEffects(lifecycle) { }` is the same.

`componentViewModel(key)` gives a self-contained, reused component (a post card with its own like and share) a
ViewModel of its own, scoped to the component rather than the screen: created when it enters composition,
cleared when it leaves, kept across a rotation. In a lazy list, `rememberItemViewModels` and `ItemScope`
keep an item's ViewModel after it scrolls away for as long as its work runs (`isBusy`) — a like being sent is
not cancelled by a scroll. `createAssistedViewModel<T, Factory> { create(routeArg) }` is for one with
arguments DI cannot inject (`assistedComponentViewModel` the same for a component):

```kotlin
@AssistedInject
class PostViewModel(@Assisted val id: String, storeFactory: StoreFactory, …) : ViewModel(), StoreOwner<…> {
    @dev.zacsweers.metro.AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class, binding = binding<ManualViewModelAssistedFactory>())
    fun interface Factory : AssistedFactory, ManualViewModelAssistedFactory {   // kit's marker + Metro's
        fun create(id: String): PostViewModel
    }
}
val viewModel = createAssistedViewModel<PostViewModel, PostViewModel.Factory> { create(route.id) }
```

## Messages and navigation

The kit has no message effect and no navigation effect, and no handlers for them. A feature declares what it
sends in its own effect type, as `HomeEffect.Message` and `HomeEffect.Navigation` above, and its screen
handles them where the snackbar host and the navigator are. Nothing about a design system or a navigator
reaches the kit, and a ViewModel's effect type says everything it can ask of its screen.

- **Message:** a `UiString` — resolve it where it is drawn (`resolve()` in Compose, `load(lookup)` in a
  snackbar callback).
- **Navigation:** a lambda over the app's navigator, so the feature writes `push(route)` as it would
  anywhere, and the screen runs it: `effect.process(navigator)`. The lambda is not comparable, so a test runs
  it against a real or fake navigator and asserts on the navigator (below).

An app that wants the same two handled the same way in every screen writes one composable for it, in its own
code, and calls it from `CollectEffects`.

## Events

```kotlin
class CartLogic(private val bus: EventBus) : Logic<CartState, CartIntent, Nothing> {
    override fun StoreScope<CartState, Nothing>.onStart() {
        observe(bus.observe<SessionExpired>()) { setState { CartState() } }
    }

    override fun StoreScope<CartState, Nothing>.onIntent(intent: CartIntent) {
        when (intent) {
            CartIntent.Checkout -> launch { bus.fire(CartChanged) }
        }
    }
}
```

## ContentState

`Idle`, `Loading(mock)`, `Success(data)`, `Error(failure)`. `dataOrNull()`, `isLoading`/`isSuccess`/…,
`map { }`, `reload()` (loading, keeping what is shown), `Result.toContentState()`. One per independently
loaded part of a screen: a profile's header and its posts load, fail and retry on their own.

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

## Testing

A `Logic` is tested with no ViewModel, through `storeTest` from `:core:testing`. It records every state change
and every effect in order; a state is asserted as the change from the one before, and a change the test did
not expect fails it.

```kotlin
@Test
fun refreshShowsPosts() = runTest {
    storeTest(HomeState(), HomeLogic(FakeLoadPosts(posts))) {
        send(HomeIntent.Refresh)
        expectState { copy(posts = ContentState.Loading()) }
        expectState { copy(posts = ContentState.Success(posts)) }
    }
}
```

A navigation effect is a lambda: take it with `nextEffect()`, run it on a navigator and assert on the navigator.

```kotlin
send(HomeIntent.Open("5"))
val effect = nextEffect() as HomeEffect.Navigation
effect.process(navigator)
assertEquals(listOf(HomeRoute, PostRoute("5")), navigator.stack)    // nav-kit's, or a fake
```

Time is the test's: `advance(millis)` moves it. `testStoreFactory()` is the real `StoreFactory` for a
ViewModel under test, and `runViewModelTest` makes `Dispatchers.Main` — which `viewModelScope` runs on — the
test's dispatcher. `StoreSettings(onError = …)` in a test collects the failures a logic lets escape; by default
a failure fails the test.

## Where things are

| Package | Holds |
|---|---|
| `store` | `Store`, `StoreScope` and its `setState`/`launch`/`observe`, `StoreOwner`, `StoreFactory`, `StoreSettings`, `StoreObserver`, `Partial` |
| `logic` | `Logic`; `logic.action` — `ActionLogic`, `Policy`; `logic.phase` — `whileIn`, `runOn` |
| `slice` | `Slice`, `Slot`, `Hosted` |
| `keeper` | `StateKeeper`: a draft that survives the process being killed |
| `compose` | `collectAsState`, `CollectEffects`, `select`, `ItemViewModels` |
| `content`, `string`, `form` | `ContentState`, `UiString`, forms with validators and transformers |
| `viewmodel` | `launch` for a ViewModel's own work, and `viewmodel.factory` — `createViewModel` and the resolver |
| `composition` | `ComponentRenderer`, `ContributedLocals` |
