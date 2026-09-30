---
name: arch-kit
description: Writes, reviews and debugs ViewModels, screens, use cases and repositories in Compose Multiplatform apps that use arch-kit (packages io.thernal.archkit.arch.*; StatePlugin, IntentPlugin, EffectPlugin, EventPlugin, StateHandler, IntentHandler, EffectHandler, setState, postIntent, launchEffect, sendMessage, navigation, OnEffectUpdate, createViewModel, ContentState, UiString, FormField, FormRule, FieldValidator, Failure, safeCall, UseCase, FlowUseCase, EventBus, Debouncer, Throttler, runViewModelTest). Use it for any presentation or domain work in such a project, even when arch-kit is not named - a new screen or feature, state and intents, one-off effects, snackbars and navigation from a ViewModel, events between features, loading/error states, error handling and mapping, forms and validation (field, cross-field, server errors), user-facing strings and translations, debounce/throttle, ViewModel creation and assisted arguments, and ViewModel tests; and for errors such as "No ViewModelFactoryRenderer installed", "No StateFactory in the plugin registry" or setState not resolving. Not for projects without arch-kit.
---

# arch-kit

arch-kit is the architecture of a Compose Multiplatform app: domain building blocks (`Failure`, `safeCall`,
use cases, `Debouncer`/`Throttler`, `EventBus`) and an MVI runtime for ViewModels (state, intents, effects,
events, `ContentState`, `UiString`, forms, DI-built ViewModels). Guide: https://github.com/Thernal/arch-kit —
`arch/presentation/api/README.md`, `arch/domain/README.md`, `arch/README.md` (why).

## 1. Orient first

```sh
grep -rln --include=*.kt -e "StatePlugin<" . | head                        # existing ViewModels — copy their shape
grep -rn --include=*.kt -e "navigation(command" -e "NavEffectHandler" .    # the app's navigation bridge
grep -rn --include=*.kt -e "MessageEffectHandler" -e "LocalStringLookup" . # how messages and keys are shown
grep -rn --include=*.kt -e "FailureReporting.reporter" -e ": PresentationLogger" .
grep -rn --include=*.kt -e "fun .*toFailure" .                             # the transport → Failure mapping
```

Nothing installed → [references/setup.md](references/setup.md). **Taken as a kit?** A `kits.lock` naming
`arch-kit` means the modules were copied renamed with skill-manager — this skill too. `skillctl.sh kit status
arch-kit` shows what moved upstream; offer `kit update arch-kit` rather than hand edits.

## 2. The model

- A ViewModel implements the plugins it needs and gets `plugins: PluginRegistry` injected; its handlers are
  built with `StateHandler { initial }`, `IntentHandler { intent -> }`, `EffectHandler()`, `EventHandler()`.
- Inside it: `setState { copy(…) }`, `launchEffect`, `sendMessage`, `navigation { }`, `fireEvent`,
  `observeEvent`, `launch { }`, `safeCall { }`. **These compile only inside a ViewModel** — by design.
- The screen: `createViewModel()`, `collectAsState()`, `postIntent(…)`, `OnEffectUpdate<E> { }` for the
  feature's own effects. Messages and navigation run through handlers the root installed.
- Repositories return `Result` from `safeCall { }`; failures are `Failure(errors, status, message)`.
- Every user-facing text is a `UiString`; remote data is a `ContentState`.

## 3. Tasks

| Task | Read |
|---|---|
| install, graph, root, navigation bridge, logging | [setup.md](references/setup.md) |
| a ViewModel and its screen, effects, events, factories, assisted arguments | [viewmodel.md](references/viewmodel.md) |
| repositories, use cases, errors, events between features, debounce/throttle | [domain.md](references/domain.md) |
| loading/error states, strings, translations, forms and validation | [forms-and-strings.md](references/forms-and-strings.md) |
| ViewModel tests | [testing.md](references/testing.md) |

## 4. Rules

- Never use `viewModelScope.launch` directly — `launch { }` reports and shows what escapes.
- Never put a resolved `String` meant for the user in state or effects — a `UiString`.
- Never hold a navigator, a `Context` or a snackbar host in a ViewModel — send an effect.
- Model one-off things (a toast, a share sheet, navigation) as effects, never as state flags.
- Never catch `CancellationException`; `safeCall` rethrows it.
- A repository never lets a transport exception out: map it to `Failure` inside `safeCall`.
- Cross-field validation is a `FormRule`, not a validator reading another field's state by hand.

## 5. Verify

```sh
./gradlew build
```
