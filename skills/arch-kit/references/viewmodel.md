# Logics, ViewModels and screens

```kotlin
data class HomeState(val posts: ContentState<List<Post>> = ContentState.Idle)

sealed interface HomeIntent {
    data object Refresh : HomeIntent
    data class Open(val id: String) : HomeIntent
    data class Share(val url: String) : HomeIntent
}

sealed interface HomeEffect {
    data class Share(val url: String) : HomeEffect
    data class Message(val message: UiString) : HomeEffect
    data class Navigation(val process: Navigator.() -> Unit) : HomeEffect
}

class HomeLogic(private val bus: EventBus, private val loadPosts: LoadPostsUseCase) :
    Logic<HomeState, HomeIntent, HomeEffect> {
    private typealias Scope = StoreScope<HomeState, HomeEffect>      // nested: one file may hold several logics

    override fun Scope.onStart() {
        observe(bus.observe<PostDeleted>()) { refresh() }
    }

    override fun Scope.onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Refresh -> refresh()
            is HomeIntent.Open -> emit(HomeEffect.Navigation { push(PostRoute(intent.id)) })
            is HomeIntent.Share -> emit(HomeEffect.Share(intent.url))
        }
    }

    private fun Scope.refresh() {
        launch {
            setState { copy(posts = posts.reload()) }
            safeCall { loadPosts(Unit) }
                .onSuccess { setState { copy(posts = ContentState.Success(it)) } }
                .onFailure { setState { copy(posts = ContentState.Error(it as Failure)) } }
        }
    }
}

@Inject
@ViewModelKey(HomeViewModel::class)
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class HomeViewModel(storeFactory: StoreFactory, bus: EventBus, loadPosts: LoadPostsUseCase) :
    ViewModel(), StoreOwner<HomeState, HomeIntent, HomeEffect> {
    override val store = store(storeFactory, HomeState(), HomeLogic(bus, loadPosts))
}
```

- The ViewModel has one line of body. Behaviour — every `setState`, `launch`, `observe` — is in the logic.
- The Metro annotations work when the graph sees this module. A feature's `impl` does not: bind the
  ViewModel in its `wiring` instead ([setup](setup.md#graph)).
- Intents run one at a time, in order, on the caller's thread: set the flag a second tap's guard reads
  (`isSending`) in the intent's synchronous part, before `launch`.
- `safeCall` returns a `Result` whose failure is a reported `Failure`; each failure ends as a state or an
  effect. A failure that escapes goes to `FailureReporting`; the store keeps working.
- A `FlowUseCase`: `useCase(params).collect { result -> … }` inside the logic — every value a success, a
  failure last.
- Effects are delivered once, to one collector; nothing to reset after showing them.
- Racing requests: `ActionLogic` with `Policy.Latest("search")` / `Ignore("submit")` / `Queue(key)` instead of a
  `Job?` field or an `isLoading` guard. Exclusive modes: a sealed state and `whileIn(Phase::class) { }`.
- Messages and navigation are effects of the feature (above); the screen handles them.

## Screen

```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel = createViewModel()) {
    val store = viewModel.store
    store.CollectEffects { effect ->
        when (effect) {
            is HomeEffect.Share -> share(effect.url)
            is HomeEffect.Message -> snackbar.showSnackbar(effect.message.load(lookup))
            is HomeEffect.Navigation -> effect.process(navigator)
        }
    }
    HomeContent(state = store.collectAsState(), onRefresh = { store.postIntent(HomeIntent.Refresh) })
}
```

Split the screen: a stateless `HomeContent(state, callbacks)` previews without a ViewModel. Neither unwraps the
state — `val state by store.collectAsState()` would recompose the route on every change — and `HomeContent`
hands each component `state.select { it.part }`, which recomposes only that component. `CollectEffects` runs only while the screen is
`RESUMED`; its block is a `CoroutineScope` — suspend in it to queue effects, `launch` to not wait.

## Factories

- `createViewModel<T>()` — the screen's; `componentViewModel<T>(key)` / `assistedComponentViewModel` — a
  self-contained, reused component's own, scoped to the component (cleared when it leaves composition).
  A lazy list's items: `rememberItemViewModels(keys)` and `ItemScope` keep an item's ViewModel while its work
  runs.
- Arguments DI cannot inject:

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

- A draft that survives the process being killed: `store(storeFactory, State(), logic, keeper =
  storeFactory.keeper(handle, "draft", Saved.serializer(), save = { … }, restore = { … }))` — keep what the user would be upset
  to lose, never a busy phase or loaded data.
