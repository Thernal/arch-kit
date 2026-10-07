package io.thernal.archkit.core.domain.usecase

/** One suspending call; return a `Result` built with `safeCall` when it can fail. */
interface SuspendUseCase<Input, Output> {
    suspend operator fun invoke(params: Input): Output
}
