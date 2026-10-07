package io.thernal.archkit.core.presentation.api.viewmodel

import androidx.lifecycle.ViewModel
import io.thernal.archkit.core.domain.safecall.safeCallFlow
import io.thernal.archkit.core.domain.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow

/** Runs a [FlowUseCase] from a ViewModel: every value as a success, a failure as the last element. */
context(_: ViewModel)
operator fun <Input, Output> FlowUseCase<Input, Output>.invoke(params: Input): Flow<Result<Output>> {
    return safeCallFlow { execute(params) }
}

context(_: ViewModel)
operator fun <Output> FlowUseCase<Unit, Output>.invoke(): Flow<Result<Output>> {
    return invoke(Unit)
}
