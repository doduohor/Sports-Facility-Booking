package com.doduohor.repository

import com.doduohor.events.ExpandAttemptResult
import com.doduohor.events.MakeAsPublishedResult
import com.doduohor.events.NewOutboxEvents
import com.doduohor.events.OutboxEvents
import com.doduohor.events.OutboxEventsRepository
import com.doduohor.events.SaveErrorResult
import com.doduohor.events.SaveEventResult
import com.doduohor.events.StartPublishingResult
import kotlin.uuid.Uuid

class InMemoryOutboxEventsRepository : OutboxEventsRepository {
    val events = mutableListOf<NewOutboxEvents>()

    override fun saveEvent(event: NewOutboxEvents): SaveEventResult {
        events += event
        return SaveEventResult.Success
    }

    override fun findUnprocessedEvents(): List<OutboxEvents> = emptyList()
    override fun tryStartPublishing(eventId: Uuid): StartPublishingResult = StartPublishingResult.NotFound
    override fun makeAsPublished(eventId: Uuid): MakeAsPublishedResult = MakeAsPublishedResult.NotFound
    override fun expandAttempt(eventId: Uuid): ExpandAttemptResult = ExpandAttemptResult.NotFound
    override fun saveError(eventId: Uuid, error: String): SaveErrorResult = SaveErrorResult.NotFound
}
