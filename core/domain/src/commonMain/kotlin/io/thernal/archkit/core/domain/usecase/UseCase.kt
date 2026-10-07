package io.thernal.archkit.core.domain.usecase

/** Synchronous, no failure modelling: a pure computation. */
interface UseCase<Input, Output> {
    operator fun invoke(params: Input): Output
}
