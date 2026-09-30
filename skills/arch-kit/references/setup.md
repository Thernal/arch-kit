# Setup

`skillctl.sh kit install arch-kit --package <app package> --module <module path> --alias <plugin alias>`;
include the eight modules; provide `kit.yml`'s `requires`: conventions `<alias>.kmp.library`,
`<alias>.compose`, `<alias>.injection`; Compose, lifecycle, coroutines, Metro + metrox-viewmodel(-compose);
Kotlin 2.4+ (context parameters, no flag).

Without skill-manager, the kit's `README.md` → Installing → *Without it* does the same by hand (copy, rename, provide).

## Feature module dependencies

```kotlin
commonMain.dependencies {
    implementation(projects.arch.presentation.api)
    implementation(projects.arch.domain)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lifecycle.runtime.compose)          // screens
}
commonTest.dependencies { implementation(projects.arch.testing) }
```

Nothing is re-exported: declare what the module's own code uses.

## Graph

`EventWiring`, `PresentationWiring` contribute to `AppScope`. The graph implements Metro's `ViewModelGraph`:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {                       // declares metroViewModelFactory
    val compositionLocals: Set<ProvidedValue<*>>
}
```

ViewModels: `@Inject @ViewModelKey(X::class) @ContributesIntoMap(AppScope::class, binding = binding<ViewModel>()) class X(override val plugins: PluginRegistry, …)` — the explicit `binding` because a ViewModel here also implements the plugin interfaces.

## Root

```kotlin
CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
    ComponentLocals(graph.compositionLocals) {
        val lookup = LocalStringLookup.current
        CompositionLocalProvider(
            LocalMessageEffectHandler provides MessageEffectHandler { snackbar.showSnackbar(it.message.load(lookup)) },
            LocalNavEffectHandler provides remember(navigator) { navEffectHandler(navigator) },
        ) { App() }
    }
}
```

Key-based translations: `LocalStringLookup provides StringLookup { key -> translations[key] }`.

## Navigation bridge (one file in the app)

```kotlin
context(_: ViewModel, plugin: EffectPlugin<*>)
fun navigation(command: Navigator.() -> Unit) {
    navigation<Navigator>(command)
}

@Suppress("UNCHECKED_CAST")
fun navEffectHandler(navigator: Navigator): NavEffectHandler {
    return NavEffectHandler { effect -> (effect as NavEffect<Navigator>).command(navigator) }
}
```

## Reporting and logging

```kotlin
FailureReporting.reporter = FailureReporter { error, isUnexpected -> crashes.record(error, isUnexpected) }   // at startup
@Provides @IntoSet fun provideConsoleLogger(): PresentationLogger { return PresentationLogger { tag, message, error -> console.log(tag, message, error) } }
```

| Error | Cause |
|---|---|
| `No ViewModelFactoryRenderer installed` | `ComponentLocals(graph.compositionLocals)` missing at the root, or `PresentationWiring` not in the graph |
| `No StateFactory in the plugin registry` | a hand-built registry without the factories — use `DefaultPluginRegistry.of(eventBus)` or the wiring |
| `setState` / `StateHandler` unresolved | called outside a ViewModel, or the class is not a `PluginContext` (a plugin interface) |
