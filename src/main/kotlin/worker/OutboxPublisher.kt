package com.doduohor.worker

import com.doduohor.events.MakeAsPublishedResult
import com.doduohor.events.OutboxEventsRepository
import com.doduohor.events.SaveErrorResult
import com.doduohor.events.StartPublishingResult
import com.doduohor.infrastructure.messaging.MessagePublisher
import com.doduohor.infrastructure.messaging.OutboxEventMapper
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class OutboxPublisher(
    private val outboxEventsRepository: OutboxEventsRepository,
    private val messagePublisher: MessagePublisher
) {
    private val logger = LoggerFactory.getLogger("OutboxPublisher")

    fun publishMessage() {
        val newEvents = outboxEventsRepository.findUnprocessedEvents()

        for (event in newEvents) {
            when (outboxEventsRepository.tryStartPublishing(event.eventId)) {
                StartPublishingResult.Started -> Unit
                StartPublishingResult.NotFound -> {
                    logger.warn("Start publishing | Event was not found: {}", event.eventId)
                    continue
                }
                StartPublishingResult.AlreadyProcessing -> {
                    logger.debug("Start publishing | Event is already processing: {}", event.eventId)
                    continue
                }
                StartPublishingResult.AlreadyPublished -> {
                    logger.debug("Start publishing | Event is already published: {}", event.eventId)
                    continue
                }
                StartPublishingResult.AttemptsExceeded -> {
                    logger.warn("Start publishing | Event reached the maximum attempts: {}", event.eventId)
                    continue
                }
                StartPublishingResult.NotNew -> {
                    logger.debug("Start publishing | Event is not new: {}", event.eventId)
                    continue
                }
            }

            try {
                val rabbitEvent = OutboxEventMapper.toRabbitMqEvent(event)
                val message = Json.encodeToString(rabbitEvent)
                messagePublisher.publish(message)
            } catch (exception: Exception) {
                val errorMessage = exception.message
                    ?: exception::class.simpleName
                    ?: "Unknown publishing error"

                when (outboxEventsRepository.saveError(event.eventId, errorMessage)) {
                    SaveErrorResult.Success -> logger.error(
                        "Publish failed and event marked as FAILED: {}",
                        event.eventId,
                        exception
                    )
                    SaveErrorResult.NotFound -> logger.warn(
                        "Save error | Event was not found: {}",
                        event.eventId
                    )
                    SaveErrorResult.NotProcessing -> logger.warn(
                        "Save error | Event is no longer PROCESSING: {}",
                        event.eventId
                    )
                }
                continue
            }

            when (outboxEventsRepository.makeAsPublished(event.eventId)) {
                MakeAsPublishedResult.Success -> logger.info(
                    "Event successfully published: {}",
                    event.eventId
                )
                MakeAsPublishedResult.ActualPublished -> logger.debug(
                    "Event was already published: {}",
                    event.eventId
                )
                MakeAsPublishedResult.NotFound -> logger.warn(
                    "Make published | Event was not found: {}",
                    event.eventId
                )
                MakeAsPublishedResult.NotProcessing -> logger.warn(
                    "Make published | Event is not PROCESSING: {}",
                    event.eventId
                )
            }
        }
    }
}
