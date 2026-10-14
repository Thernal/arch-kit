# Setup

`skillctl.sh kit install arch-kit --package <app package> --alias <plugin alias>` — no `--module`: the
modules land at `core/domain`, `core/event/*`, `core/presentation/*`, `core/testing` (ArenaGo's layout), and
a different module path makes paths and packages disagree. An app module already at one of those paths (a
strings module at `core/presentation`) is moved first. Include the eight modules; provide `kit.yml`'s `requires`: conventions `<alias>.kmp.library`,
`<alias>.compose`, `<alias>.injection`; Compose, lifecycle, coroutines, Metro + metrox-viewmodel(-compose);
Kotlin 2.4+ (context parameters, no flag).

Without skill-manager, the kit's `README.md` → Installing → *Without it* does the same by hand (copy, rename, provide).

## Feature module dependencies

```kotlin
commonMain.dependencies {
    implementation(projects.core.presentation.api)
    implementation(projects.core.domain)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lifecycle.runtime.compose)          // screens
}
commonTest.dependencies { implementation(projects.core.testing) }
```

Nothing is re-exported: declare what the module's own code uses.

## Graph

`EventProvidersModule`, `PresentationProvidersModule` contribute to `AppScope`. The graph implements Metro's `ViewModelGraph`:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {                       // declares metroViewModelFactory
    val compositionLocals: Set<ProvidedValue<*>>
}
```

ViewModels in the app's own module (the graph's module or one it depends on directly):
`@Inject @ViewModelKey(X::class) @ContributesIntoMap(AppScope::class, binding = binding<ViewModel>()) class X(storeFactory: StoreFactory, …)`
— the explicit `binding` because a ViewModel here also implements `StoreOwner`.

ViewModels of a feature split into `api`/`impl`/`wiring`: the app depends on `wiring`, and `impl` is
`wiring`'s `implementation` dependency, so `impl` is not on the graph's compile classpath. Metro finds
contributions on that classpath only: a `@ContributesIntoMap` in `impl` compiles and never reaches the map
(the screen fails at runtime). Leave the class unannotated and bind it in `wiring`:

```kotlin
@BindingContainer
@ContributesTo(AppScope::class)
interface ProfileViewModelsModule {
    companion object {
        @Provides
        @IntoMap
        @ViewModelKey(ProfileViewModel::class)
        fun provideProfileViewModel(storeFactory: StoreFactory): ViewModel {
            return ProfileViewModel(storeFactory = storeFactory)
        }

        @Provides
        @IntoMap
        @ManualViewModelAssistedFactoryKey(PostViewModel.Factory::class)
        fun providePostViewModelFactory(storeFactory: StoreFactory): ManualViewModelAssistedFactory {
            return PostViewModel.Factory { id -> PostViewModel(id = id, storeFactory = storeFactory) }
        }
    }
}
```

## Root

```kotlin
CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
    ContributedLocals(graph.compositionLocals) { App() }
}
```

Nothing for messages or navigation: the kit has none. A screen handles its own `Message` and `Navigation`
effects (see viewmodel.md); an app that wants one shared handler writes a composable of its own and calls it
from `CollectEffects`.

Key-based translations: `LocalStringLookup provides StringLookup { key -> translations[key] }`.

## Reporting and observing

```kotlin
FailureReporting.reporter = FailureReporter { error, isUnexpected -> crashes.record(error, isUnexpected) }   // at startup
@Provides @IntoSet fun provideConsoleObserver(): StoreObserver = object : StoreObserver {
    override fun onIntent(intent: Any?) { console.log("intent $intent") }
    override fun onState(partial: Partial<*>, previous: Any?, state: Any?) { console.log("$partial: $previous -> $state") }
    override fun onEffect(effect: Any?) { console.log("effect $effect") }
}
```

A failure a logic lets escape goes to `FailureReporting`, and the store keeps working.

| Error | Cause |
|---|---|
| `No ViewModelResolver installed` | `ContributedLocals(graph.compositionLocals)` missing at the root, or `PresentationProvidersModule` not in the graph |
| `The effects already have a collector` | `CollectEffects` called twice for one store — collect in one place |
| `setState` / `emit` unresolved | called outside a `Logic`'s `onStart`/`onIntent` — they are members of `StoreScope` |
