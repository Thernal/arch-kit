# arch — design

Why the kit has the shape it has. The kit is ArenaGo's `core/domain`, `core/event` and
`core/presentation` ported to Compose Multiplatform, with the choices where Act2Act differed decided
explicitly (epic decisions D40–D44).

## Handlers that only compile in a ViewModel

`StateHandler { }`, `IntentHandler { }`, `EffectHandler()`, `EventHandler()`, `setState`, `launchEffect`,
`sendMessage` and `navigation` take the ViewModel and its `PluginContext` as context parameters. Inside a
ViewModel both are the implicit `this`, so the calls read like members; anywhere else they do not type-check.
"State changes only happen in a ViewModel" is a compile error, not a review comment. Context parameters need
no compiler flag on Kotlin 2.4 — checked on 2.4.10 and 2.4.20, JVM and iOS.

The handlers come from factories in a `PluginRegistry` the ViewModel is given (`override val plugins`), so
logging, testing and a future replacement change one binding, not every ViewModel. A new plugin kind is a
new factory contributed into the registry — nothing in the kit changes.

## Effects: delivered once, handled by the app

Intents go through an unbounded channel: one posted before anything collects is not lost, and they run in
order. Effects go through a buffered channel to exactly one collector: a message is not shown twice after a
rotation, and one sent while the screen is paused is shown when it resumes (`repeatOnLifecycle(RESUMED)`).

The kit names no design system and no navigator. `MessageEffect` goes to a `MessageEffectHandler` and
`NavEffect<N>` to a `NavEffectHandler`, both composition locals the root installs; by default both do
nothing, so previews compose. `NavEffect` is generic over the navigator type: ArenaGo's `navigation { push() }`
stays as it was, with nav-kit or any other navigator, through a one-file bridge in the app.

## Errors never crash the screen

`ViewModel.launch` and the intent handler catch what escapes a coroutine or an intent, report it to
`FailureReporting`, and — in an effect plugin — show it as an error message. A forgotten `try` is a message,
not a crash. `EffectPlugin.safeCall` does the same for one call, and returns the `Result`.

## Strings are resolved where they are drawn

State, effects and form errors carry `UiString`, never a resolved string: a ViewModel does not touch
resources, and a test compares values. ArenaGo's `@StringRes` is Compose Resources' `StringResource` here.
The kit's own texts — validator messages, failure fallbacks — are `Localized(key, fallback)`: an app with
key-based translations resolves `validation.email` through its `StringLookup`, every other app shows the
English fallback. The kit ships no resource files: resources in a renamed, copied module merge badly; keys
do not.

## Forms have two kinds of rules

A field validator sees one value; `+` combines them, first message wins. What spans fields — "repeat must
match", "end after start" — is a `FormRule`, which returns the form with errors set where they belong. A
server's field errors land on fields the same way (`withServerErrors`), and the ones the form does not show
come back for a message.

## The event bus has one stream

ArenaGo kept one flow per event class, created lazily without a lock, so an observer of a supertype never
saw its subtypes. Here there is one shared flow and each observer filters by type.

## One concept per file, packages by role

Each file holds one type, or one type and the composition local that installs it; a type's
extensions and its DSL get a file of their own beside it (`StateDsl.kt`, `ContentStateExtensions.kt`),
so a contract reads without its helpers and a helper is found by name. Packages say what a part is
for: `plugin` is the machinery, and state, intent, effect and event sit under it as equals, each with
its own contract (`ViewState` lives with the state plugin, not in a shared `model`). The presentation
modules are the presentation layer by name, so their packages start right under the module
(`core.presentation.api.plugin`), never `core.presentation.api.presentation` — the layer Detekt rule
reads the layer off the module name and reports a repeated one. `composition` holds
what every feature builds on — `ComponentRenderer` and `ContributedLocals` — and the default handlers
in `impl` mirror the `plugin` packages one to one.

`ViewModelResolver` is not a renderer: it draws nothing, it hands a composition its ViewModel. The
`Renderer` suffix is kept for contracts that do draw, which is what lets `ComponentRenderer` mean one
thing.

## What changed besides

- `FlowUseCase.execute` and `safeCallFlow` are not `suspend`: a flow is cold, and Detekt is right that a
  suspending function returning one is a smell.
- Logging goes to `PresentationLogger`s the app contributes, and failures to a `FailureReporter` — the kit
  depends on no console.
- Paging entities stay with paging-kit; `PreventScreenshot`, list helpers and `NavigationState` (Android- or
  navigator-specific) are not here.
