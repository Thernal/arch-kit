package io.thernal.archkit.core.domain.usecase

import kotlinx.coroutines.flow.Flow

/**
 * A stream; a ViewModel collects it through the `invoke` extension in core/presentation. Not
 * `suspend`: a flow is cold, and whatever it needs to do first belongs inside it.
 */
interface FlowUseCase<Input, Output> {
    fun execute(params: Input): Flow<Output>
}
