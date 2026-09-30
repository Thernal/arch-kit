# Preview providers and scoped ViewModel owners

**Status:** open

ArenaGo also had a `ContentStateParameterProvider` (a preview through every `ContentState`) and
`ScopedViewModelStoreOwner` (keeping a ViewModel across a nested navigation host). Neither came along: the
first needs `ui-tooling-preview` in `api` (paging-kit keeps its preview provider in a separate `preview`
module — the same shape would fit here), the second belongs next to the navigator. Decide when an app needs
them.
