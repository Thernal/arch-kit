# Agent skills

Skills for AI coding agents working in projects that **use** arch-kit, in the
[Agent Skills](https://agentskills.io) layout.

| Skill | Use it when |
|---|---|
| [`arch-kit`](arch-kit/SKILL.md) | writing or reviewing ViewModels, screens, use cases and repositories; state, intents, effects, events; errors and `safeCall`; forms and validation; `UiString`; ViewModel factories; testing ViewModels |

```
arch-kit/
├── SKILL.md                    the model, orientation, rules, task router
└── references/
    ├── setup.md                modules, dependencies, graph, root, the navigation bridge
    ├── viewmodel.md            plugins, state, intents, effects, events, launch/safeCall, screens, factories
    ├── domain.md               Failure, safeCall, use cases, Debouncer/Throttler, EventBus, mapping errors
    ├── forms-and-strings.md    ContentState, UiString, validators, transformers, FormRule, server errors
    └── testing.md              testPlugins, runViewModelTest, recordEffects
```
