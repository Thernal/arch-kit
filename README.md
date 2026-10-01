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
| [`core/presentation/api/README.md`](core/presentation/api/README.md) | the MVI runtime, forms, strings, ViewModel factories — task by task |
| [`core/domain/README.md`](core/domain/README.md) | `Failure`, `safeCall`, use cases, concurrency helpers, the event bus |
| [`core/README.md`](core/README.md) | why each part has its shape, and what changed from the apps it came from |
| [`skills/arch-kit`](skills/arch-kit/SKILL.md) | the same for an agent working in an app that uses the kit |

## Installing

An application takes the kit **by copy**, not as a dependency: the code is copied into the app, renamed to the app's own package, and belongs to the app from then on. Nothing is published to a Maven repository.

### Where it lands

The kit's modules already sit where an app keeps them, so their paths are not renamed:
`core/domain`, `core/event/{api,impl,wiring}`, `core/presentation/{api,impl,wiring}`, `core/testing` —
packages `<app package>.core.domain…`, `<app package>.core.presentation.api…`. This is ArenaGo's layout,
the app the kit was taken from. Keep it: a module path of its own (`:core:arch`, `:arch`) puts an extra
segment in every path and none in the packages, which then no longer match.

**Before installing**, the app must not have modules of its own at those paths. A common one is an app's
strings module at `core/presentation` (Compose Resources): move it first (`core/resources`, `core/ui`) —
the kit's `core/presentation/api` would otherwise be created inside it.

### With skill-manager

If you have access to the author's knowledge repository (`github.com/Thernal/knowledge`), its **skill-manager** skill does all of it — copy, rename, the skill, and later updates:

```sh
skillctl.sh kit install arch-kit --package com.example.app --alias app
```

No `--module`: the default, `:core`, is the layout above. It copies the `code` parts of [`kit.yml`](kit.yml) renamed, installs the `arch-kit` skill and records the copy in `kits.lock`. `kit status` then shows what changed upstream and what the app edited; `kit update` merges the kit's changes three ways, keeping the app's edits. The install prints what the app must provide (`requires`).

### Without it

The same by hand, from a clone of this repository.

1. **Copy** the paths listed under `code` in [`kit.yml`](kit.yml) into the app at the same paths (`core/…`). Note the commit you copied (`git rev-parse HEAD`) — updates start from it.
2. **Rename** in everything copied:

   | In the kit | Becomes | Where |
   |---|---|---|
   | `io.thernal.archkit` | the app's package, e.g. `com.example.app` | sources, build files; and the directories `io/thernal/archkit` |
   | `libs.plugins.archkit.` | the app's catalog alias, e.g. `libs.plugins.app.` | build files |

   ```sh
   # in the app, after copying — perl, so it runs the same on macOS and Linux
   parts="core/domain core/event core/presentation core/testing"
   grep -rlI -e io.thernal.archkit -e io/thernal/archkit -e plugins.archkit. $parts \
     | xargs perl -pi -e 's/\Qio.thernal.archkit\E/com.example.app/g; s{\Qio/thernal/archkit\E}{com/example/app}g; s/libs\.plugins\.\Qarchkit\E\./libs.plugins.app./g'
   find $parts -depth -type d -path '*/io/thernal/archkit' | while read -r d; do
     mkdir -p "${d%/io/thernal/archkit}/com/example" && mv "$d" "${d%/io/thernal/archkit}/com/example/app"
   done
   find $parts -depth -type d -empty -delete
   ```

3. **Provide** what the copy expects — the `requires` list in [`kit.yml`](kit.yml): convention plugins (build-kit's, or the ones in this repository's `build-logic/convention`), catalog entries, settings — and, where listed, platform setup.
4. **The skill** (optional): copy [`skills/arch-kit`](skills/arch-kit) into the app's skills directory (`.claude/skills/` for Claude Code), with the same renames, so an agent working in the app knows the kit.
5. **Updates** are yours to carry: `git diff <the commit you copied> <a newer one> -- <the code paths>` in the kit shows what changed; apply what you want, renamed the same way.

## Layout

| Module | Holds | Depends on |
|---|---|---|
| `core/domain` | `Failure`/`FieldError`/`ResponseStatus`, `safeCall`/`runSafeCall`/`safeCallFlow`, `FailureReporter`, `UseCase`/`SuspendUseCase`/`FlowUseCase`, `Debouncer`, `Throttler`, `asEnum` — pure Kotlin | coroutines |
| `core/event/{api,impl,wiring}` | `EventBus`, `BaseEvent`; `SharedFlowEventBus`; its binding | coroutines |
| `core/presentation/api` | contracts, plugins and their DSL, effects, `ContentState`, `UiString`, forms, ViewModel factories, render contracts | domain, event/api, Compose, lifecycle |
| `core/presentation/impl` | the handlers, the plugin registry, the Metro ViewModel factory | api, metrox-viewmodel |
| `core/presentation/wiring` | the registry, factories, loggers set, `MetroViewModelFactory`, the renderer local | api, impl |
| `core/testing` | `testPlugins()`, `runViewModelTest`, `recordEffects` | presentation, event |

## Building

```sh
./gradlew build
```

Every target, tests on the JVM host and the iOS simulator — the domain helpers, the event bus, forms and
strings, and a sign-up ViewModel driven end to end (state, intents, effects, navigation, events, field and
form rules, server field errors, an intent that throws) — and Detekt, which fails on any finding
(`-PdetektAutoCorrect=true` fixes formatting first). The Gradle daemon runs on JDK 21 (Metro).
