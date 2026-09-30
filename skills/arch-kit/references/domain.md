# Domain

## Repositories

```kotlin
override suspend fun post(id: String): Result<Post> {
    return safeCall { api.post(id).toDomain() }
}
override fun posts(): Flow<Result<List<Post>>> {
    return safeCallFlow { dao.observe().map { rows -> rows.map { it.toDomain() } } }
}
```

`safeCall` → `Result`: success, the thrown `Failure`, or anything else as `Failure.of(it)`; cancellation
rethrown; every failure reported to `FailureReporting.reporter`.

Map transport errors once, inside `safeCall` (with network-kit: `NetworkException` → `Failure`):

```kotlin
fun NetworkException.toFailure(): Failure {
    val http = error as? NetworkError.Http
    return Failure(
        errors = http?.body?.fields.orEmpty().map { FieldError(field = it.field, error = it.error.orEmpty(), code = it.code) },
        status = ResponseStatus(code = http?.body?.code ?: -1, error = http?.body?.error ?: error::class.simpleName),
        message = http?.body?.message ?: message,
        cause = this,
    )
}
```

## Use cases

`UseCase` (pure), `SuspendUseCase` (one call, returns a `Result` when it can fail), `FlowUseCase.execute`
(a stream; not `suspend`). Keep one per user action; a use case with nothing but a pass-through call is
optional — call the repository.

## Events between features

`data object SessionExpired : BaseEvent`; in a ViewModel `fireEvent(SessionExpired)` /
`observeEvent<SessionExpired> { }`; elsewhere `eventBus.fire(…)` / `eventBus.observe<T>()`. Observers see
subtypes. Not state: nobody listening means the event is gone.

## Debouncer / Throttler

`Debouncer(300.milliseconds, viewModelScope)` — the last call of a burst; `Throttler(1.seconds)` — the first,
the rest dropped (`tryAcquire()` for the boolean).
