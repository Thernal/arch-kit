# ViewModels and screens

```kotlin
class HomeViewModel(
    override val plugins: PluginRegistry,
    private val loadPosts: LoadPostsUseCase,
) : ViewModel(), StatePlugin<HomeState>, IntentPlugin<HomeIntent>, EffectPlugin<HomeEffect>, EventPlugin {
    override val stateHandler = StateHandler { HomeState() }
    override val effectHandler = EffectHandler<HomeEffect>()
    override val eventHandler = EventHandler()
    override val intentHandler = IntentHandler<HomeIntent> { intent ->
        when (intent) {
            HomeIntent.Refresh -> refresh()
            is HomeIntent.Open -> navigation { push(PostRoute(intent.id)) }
            is HomeIntent.Share -> launchEffect(HomeEffect.Share(intent.url))
        }
    }

    init {
        observeEvent<PostDeleted> { refresh() }
    }

    private fun refresh() {
        launch {
            setState { copy(posts = posts.reload()) }
            safeCall { loadPosts(Unit) }
                .onSuccess { setState { copy(posts = ContentState.Success(it)) } }
                .onFailure { setState { copy(posts = ContentState.Error(it as Failure)) } }
        }
    }
}
```

- Implement only the plugins used; `plugins` is overridden once for all of them.
- Intents run one at a time, in order; an exception thrown in the handler is reported and shown, and the next
  intent still runs.
- `safeCall` in an effect plugin shows the failure as an error message *and* returns the `Result`.
- A `FlowUseCase`: `useCase(params).collect { result -> … }` — every value a success, a failure last.
- Effects are delivered once, to the resumed screen; nothing to reset after showing them.

## Screen

```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel = createViewModel()) {
    val state by viewModel.collectAsState()
    viewModel.OnEffectUpdate<HomeEffect> { effect -> when (effect) { is HomeEffect.Share -> share(effect.url) } }
    HomeContent(state = state, onRefresh = { viewModel.postIntent(HomeIntent.Refresh) })
}
```

Split the screen: a stateless `HomeContent(state, callbacks)` previews without a ViewModel.
`collectAsState { it.part }` recomposes only for that part.

## Factories

- `createViewModel<T>()` — the screen's; `componentViewModel<T>(key)` — one per component instance.
- Arguments DI cannot inject:

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
