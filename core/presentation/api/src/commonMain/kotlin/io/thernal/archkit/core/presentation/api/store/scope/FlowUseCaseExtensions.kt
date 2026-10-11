package io.thernal.archkit.core.presentation.api.store.scope

import io.thernal.archkit.core.domain.safecall.safeCallFlow
import io.thernal.archkit.core.domain.usecase.FlowUseCase
import io.thernal.archkit.core.presentation.api.logic.Logic
import kotlinx.coroutines.flow.Flow

/** Runs a [FlowUseCase] from a [Logic]: every value as a success, a failure as the last element. */
context(_: StoreScope<*, *>)
operator fun <Input, Output> FlowUseCase<Input, Output>.invoke(params: Input): Flow<Result<Output>> {
    return safeCallFlow { execute(params) }
}

context(_: StoreScope<*, *>)
operator fun <Output> FlowUseCase<Unit, Output>.invoke(): Flow<Result<Output>> {
    return invoke(Unit)
}
