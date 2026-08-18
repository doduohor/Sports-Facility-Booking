package com.doduohor.worker

import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.events.IncidentEventPayload
import com.doduohor.events.MeasurementEventPayload
import com.doduohor.infrastructure.messaging.RabbitMqEvent
import com.doduohor.infrastructure.messaging.RabbitMqEventType
import com.doduohor.infrastructure.notification.NotificationSender
import com.doduohor.infrastructure.notification.NotificationSenderResult
import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.EventHistoryStatus
import com.doduohor.repository.mongo.EventHistoryRepository
import com.doduohor.repository.mongo.MarkProcessedResult
import com.doduohor.repository.mongo.MarkStartProcessingResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.time.Instant

class MessageHandler(
    private val notificationSender: NotificationSender,
    private val eventHistoryRepository: EventHistoryRepository
) {
    private val logger = LoggerFactory.getLogger("MessageHandler")
    suspend fun handle(message: String): MessageHandlerResult{
        var eventId: String? = null
        try{
            val rabbitEvent = Json.decodeFromString<RabbitMqEvent>(message)
            eventId = rabbitEvent.eventId
            when (eventHistoryRepository.tryStartProcessing(
                EventHistoryDocument(
                    eventId = rabbitEvent.eventId,
                    eventType = rabbitEvent.eventType.name,
                    eventCreatedAt = rabbitEvent.createdAt,
                    receivedAt = Instant.now().toString(),
                    processedAt = null,
                    processingStartedAt = Instant.now().toString(),
                    data = rabbitEvent.data.jsonObject,
                    status = EventHistoryStatus.PROCESSING,
                    errorMessage = null,
                    attempt = 1
                )
            )) {
                MarkStartProcessingResult.Started -> Unit
                MarkStartProcessingResult.AlreadyProcessing,
                MarkStartProcessingResult.AlreadyProcessed,
                MarkStartProcessingResult.AttemptsExceeded -> {
                    logger.info("Event is not available for processing: {}", rabbitEvent.eventId)
                    return MessageHandlerResult.Success
                }
            }

            val result = when(rabbitEvent.eventType){
                RabbitMqEventType.MEASUREMENT_CREATED ->
                {
                    logger.info("Measurement created: {}", Json.decodeFromJsonElement<MeasurementEventPayload>(rabbitEvent.data))
                    MessageHandlerResult.Success
                }
                RabbitMqEventType.INCIDENT_CREATED ->
                {
                    val incidentPayload = Json.decodeFromJsonElement<IncidentEventPayload>(rabbitEvent.data)
                    logger.info("Incident created: {}", incidentPayload)
                    processIncidentCreated(incidentPayload)
                }
            }

            return when(result) {
                MessageHandlerResult.Success -> {
                    when (eventHistoryRepository.markProcessed(rabbitEvent.eventId)) {
                        MarkProcessedResult.Updated -> MessageHandlerResult.Success
                        MarkProcessedResult.NotFound,
                        MarkProcessedResult.NotProcessing -> {
                            logger.error("Failed to mark event as PROCESSED: {}", rabbitEvent.eventId)
                            MessageHandlerResult.Failure
                        }
                    }
                }
                MessageHandlerResult.Failure -> {
                    eventHistoryRepository.markFailed(rabbitEvent.eventId, "Event processing failed")
                    MessageHandlerResult.Failure
                }
            }
        }catch (exception: Exception){
            logger.error("Parse Json ended with an error", exception)
            eventId?.let { eventHistoryRepository.markFailed(it, exception.message ?: "Unknown error") }
            return MessageHandlerResult.Failure
        }
    }
    
    private fun processIncidentCreated(incidentEvent: IncidentEventPayload): MessageHandlerResult {
        if(incidentEvent.severity == IncidentSeverity.HIGH || incidentEvent.severity == IncidentSeverity.CRITICAL){
            when(val notificationResult = notificationSender.send(Json.encodeToString(incidentEvent))){
                is NotificationSenderResult.Failure -> {
                    logger.error("Failed to send incident notification: {}", notificationResult.reason)
                    return MessageHandlerResult.Failure
                }
                NotificationSenderResult.Success -> {}
            }
            logger.warn("Important incident detected: {}", incidentEvent)
        }
        return MessageHandlerResult.Success
    }
}

sealed interface MessageHandlerResult {
    data object Success: MessageHandlerResult
    data object Failure: MessageHandlerResult
}
