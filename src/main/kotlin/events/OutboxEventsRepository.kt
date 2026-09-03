package com.doduohor.events

import kotlin.uuid.Uuid

interface OutboxEventsRepository {
    fun saveEvent(event: NewOutboxEvents): SaveEventResult
    fun findUnprocessedEvents(): List<OutboxEvents>
    fun tryStartPublishing(eventId: Uuid): StartPublishingResult
    fun makeAsPublished(eventId: Uuid): MakeAsPublishedResult
    fun expandAttempt(eventId: Uuid): ExpandAttemptResult
    fun saveError(eventId: Uuid, error: String): SaveErrorResult
}

sealed interface SaveEventResult{
    data object Error: SaveEventResult
    data object Success: SaveEventResult
}

sealed interface MakeAsPublishedResult{
    data object ActualPublished: MakeAsPublishedResult
    data object NotProcessing: MakeAsPublishedResult
    data object Success: MakeAsPublishedResult
    data object NotFound: MakeAsPublishedResult
}

sealed interface ExpandAttemptResult{
    data object Success: ExpandAttemptResult
    data object NotFound: ExpandAttemptResult
    data object NotProcessing: ExpandAttemptResult
}

sealed interface StartPublishingResult {
    data object Started: StartPublishingResult
    data object NotFound: StartPublishingResult
    data object AlreadyProcessing: StartPublishingResult
    data object AlreadyPublished: StartPublishingResult
    data object AttemptsExceeded: StartPublishingResult
    data object NotNew: StartPublishingResult
}

sealed interface SaveErrorResult{
    data object Success: SaveErrorResult
    data object NotFound: SaveErrorResult
    data object NotProcessing: SaveErrorResult
}
