package com.doduohor.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun initializeMongoEventHistory(
    repository: com.doduohor.repository.mongo.EventHistoryRepository,
    maxAttempts: Int = 5,
    retryDelay: suspend () -> Unit = { delay(2_000) },
    onFailure: (attempt: Int, maxAttempts: Int, exception: Throwable) -> Unit = { _, _, _ -> }
) {
    require(maxAttempts > 0) { "maxAttempts must be positive" }

    var lastFailure: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            repository.createIndexes()
            return
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            lastFailure = exception
            onFailure(attempt + 1, maxAttempts, exception)
            if (attempt + 1 < maxAttempts) {
                retryDelay()
            }
        }
    }

    throw checkNotNull(lastFailure)
}
