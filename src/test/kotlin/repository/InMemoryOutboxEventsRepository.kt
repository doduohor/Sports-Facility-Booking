package com.doduohor.repository

import com.doduohor.events.ExpandAttemptResult
import com.doduohor.events.MakeAsPublishedResult
import com.doduohor.events.NewOutboxEvents
import com.doduohor.events.OutboxEvents
import com.doduohor.events.OutboxEventsRepository
import com.doduohor.events.SaveErrorResult
import com.doduohor.events.SaveEventResult
import com.doduohor.events.StartPublishingResult
import com.doduohor.events.OutboxEventStatus
import com.doduohor.events.EventProcessingPolicy
import com.doduohor.domain.shared.Clock
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class InMemoryOutboxEventsRepository(private val clock: Clock) : OutboxEventsRepository {
    private val storedEvents = linkedMapOf<Uuid, OutboxEvents>()
    val events: List<OutboxEvents> get() = storedEvents.values.toList()

    override fun saveEvent(event: NewOutboxEvents): SaveEventResult {
        if (storedEvents.containsKey(event.eventId)) return SaveEventResult.Error
        storedEvents[event.eventId] = OutboxEvents(
            id = storedEvents.size.toLong() + 1,
            eventId = event.eventId,
            eventType = event.eventType,
            payload = event.payload,
            status = event.status,
            createdAt = event.createdAt,
            publishedAt = event.publishedAt,
            attempt = event.attempt,
            errorMessage = event.errorMessage
        )
        return SaveEventResult.Success
    }

    override fun findUnprocessedEvents(): List<OutboxEvents> = storedEvents.values.filter {
        it.status == OutboxEventStatus.NEW ||
            (it.status == OutboxEventStatus.FAILED && it.attempt < EventProcessingPolicy.MAX_ATTEMPTS)
    }

    override fun tryStartPublishing(eventId: Uuid): StartPublishingResult {
        val event = storedEvents[eventId] ?: return StartPublishingResult.NotFound
        return when (event.status) {
            OutboxEventStatus.NEW, OutboxEventStatus.FAILED -> {
                if (event.attempt >= EventProcessingPolicy.MAX_ATTEMPTS) StartPublishingResult.AttemptsExceeded
                else {
                    storedEvents[eventId] = event.copy(
                        status = OutboxEventStatus.PROCESSING,
                        attempt = event.attempt + 1,
                        publishedAt = null,
                        errorMessage = null
                    )
                    StartPublishingResult.Started
                }
            }
            OutboxEventStatus.PROCESSING -> StartPublishingResult.AlreadyProcessing
            OutboxEventStatus.PUBLISHED -> StartPublishingResult.AlreadyPublished
        }
    }

    override fun makeAsPublished(eventId: Uuid): MakeAsPublishedResult {
        val event = storedEvents[eventId] ?: return MakeAsPublishedResult.NotFound
        return when (event.status) {
            OutboxEventStatus.PROCESSING -> {
                storedEvents[eventId] = event.copy(
                    status = OutboxEventStatus.PUBLISHED,
                    publishedAt = OffsetDateTime.ofInstant(clock.now(), java.time.ZoneOffset.UTC),
                    errorMessage = null
                )
                MakeAsPublishedResult.Success
            }
            OutboxEventStatus.PUBLISHED -> MakeAsPublishedResult.ActualPublished
            else -> MakeAsPublishedResult.NotProcessing
        }
    }

    override fun expandAttempt(eventId: Uuid): ExpandAttemptResult {
        val event = storedEvents[eventId] ?: return ExpandAttemptResult.NotFound
        if (event.status != OutboxEventStatus.PROCESSING) return ExpandAttemptResult.NotProcessing
        storedEvents[eventId] = event.copy(attempt = event.attempt + 1)
        return ExpandAttemptResult.Success
    }

    override fun saveError(eventId: Uuid, error: String): SaveErrorResult {
        val event = storedEvents[eventId] ?: return SaveErrorResult.NotFound
        if (event.status != OutboxEventStatus.PROCESSING) return SaveErrorResult.NotProcessing
        storedEvents[eventId] = event.copy(status = OutboxEventStatus.FAILED, errorMessage = error, publishedAt = null)
        return SaveErrorResult.Success
    }
}
