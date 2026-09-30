package io.thernal.archkit.arch.domain.usecase

import kotlinx.coroutines.flow.Flow

/** Synchronous, no failure modelling: a pure computation. */
interface UseCase<Input, Output> {
    operator fun invoke(params: Input): Output
}

/** One suspending call; return a `Result` built with `safeCall` when it can fail. */
interface SuspendUseCase<Input, Output> {
    suspend operator fun invoke(params: Input): Output
}

/**
 * A stream; a ViewModel collects it through the `invoke` extension in arch/presentation. Not
 * `suspend`: a flow is cold, and whatever it needs to do first belongs inside it.
 */
interface FlowUseCase<Input, Output> {
    fun execute(params: Input): Flow<Output>
}
