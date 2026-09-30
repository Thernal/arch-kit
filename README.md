# arch-kit

The architecture every feature of a Compose Multiplatform app (android, iosArm64, iosSimulatorArm64) is
built on: domain building blocks — `Failure`, `safeCall`, use cases, `Debouncer`/`Throttler` — an event bus
between features, and an MVI runtime for ViewModels: state, intents, one-off effects, events, `ContentState`
for remote data, `UiString` for every text, forms with field and form-level rules, and DI-built ViewModels.

```kotlin
class ProfileViewModel(
    override val plugins: PluginRegistry,
    private val loadProfile: LoadProfileUseCase,
) : ViewModel(), StatePlugin<ProfileState>, IntentPlugin<ProfileIntent>, EffectPlugin<ProfileEffect> {
    override val stateHandler = StateHandler { ProfileState() }
    override val effectHandler = EffectHandler<ProfileEffect>()
    override val intentHandler = IntentHandler<ProfileIntent> { intent ->
        when (intent) {
            ProfileIntent.Refresh -> refresh()
            ProfileIntent.Edit -> navigation<Navigator> { push(EditProfileRoute) }
        }
    }

    private fun refresh() {
        launch {
            setState { copy(profile = profile.reload()) }
            safeCall { loadProfile(Unit) }.onSuccess { setState { copy(profile = ContentState.Success(it)) } }
        }
    }
}
```

`setState`, `launchEffect`, `sendMessage`, `navigation` and the handler builders compile only inside a
ViewModel — Kotlin context parameters, no compiler flag.

## Documentation

| Read | For |
|---|---|
| this file | what is here and how it is built |
| [`arch/presentation/api/README.md`](arch/presentation/api/README.md) | the MVI runtime, forms, strings, ViewModel factories — task by task |
| [`arch/domain/README.md`](arch/domain/README.md) | `Failure`, `safeCall`, use cases, concurrency helpers, the event bus |
| [`arch/README.md`](arch/README.md) | why each part has its shape, and what changed from the apps it came from |
| [`skills/arch-kit`](skills/arch-kit/SKILL.md) | the same for an agent working in an app that uses the kit |

## For AI agents

An application takes the kit by copy: `skillctl.sh kit install arch-kit --package <its package> --module
<its module path> --alias <its plugin alias>` copies the modules below renamed, installs the `arch-kit`
skill, and records both in `kits.lock`. `kit.yml` lists what the copied modules expect.

## Layout

| Module | Holds | Depends on |
|---|---|---|
| `arch/domain` | `Failure`/`FieldError`/`ResponseStatus`, `safeCall`/`runSafeCall`/`safeCallFlow`, `FailureReporter`, `UseCase`/`SuspendUseCase`/`FlowUseCase`, `Debouncer`, `Throttler`, `asEnum` — pure Kotlin | coroutines |
| `arch/event/{api,impl,wiring}` | `EventBus`, `BaseEvent`; `SharedFlowEventBus`; its binding | coroutines |
| `arch/presentation/api` | contracts, plugins and their DSL, effects, `ContentState`, `UiString`, forms, ViewModel factories, render contracts | domain, event/api, Compose, lifecycle |
| `arch/presentation/impl` | the handlers, the plugin registry, the Metro ViewModel factory | api, metrox-viewmodel |
| `arch/presentation/wiring` | the registry, factories, loggers set, `MetroViewModelFactory`, the renderer local | api, impl |
| `arch/testing` | `testPlugins()`, `runViewModelTest`, `recordEffects` | presentation, event |

## Building

```sh
./gradlew build
```

Every target, tests on the JVM host and the iOS simulator — the domain helpers, the event bus, forms and
strings, and a sign-up ViewModel driven end to end (state, intents, effects, navigation, events, field and
form rules, server field errors, an intent that throws) — and Detekt, which fails on any finding
(`-PdetektAutoCorrect=true` fixes formatting first). The Gradle daemon runs on JDK 21 (Metro).
