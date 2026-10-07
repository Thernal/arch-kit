# Preview providers and ViewModel owners across nested hosts

**Status:** open

ArenaGo also had a `ContentStateParameterProvider` (a preview through every `ContentState`) and
`ScopedViewModelStoreOwner` (keeping a ViewModel across a nested navigation host). Neither came along: the
first needs `ui-tooling-preview` in `api` (paging-kit keeps its preview provider in a separate `preview`
module — the same shape would fit here), the second belongs next to the navigator. Decide when an app needs
them.

Settled 2026-10-07: a component's own ViewModel is scoped to the component — `componentViewModel` and
`assistedComponentViewModel` take `rememberViewModelStoreOwner()` (lifecycle 2.11), as Act2Act does. What
stays open is only the nested-host case above.
