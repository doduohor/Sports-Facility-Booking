package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.shared.Clock
import com.doduohor.events.EventPublisher
import com.doduohor.events.IntegrationEventType
import com.doduohor.events.NewOutboxEvents
import com.doduohor.events.OutboxEventsRepository
import com.doduohor.events.SaveEventResult
import com.doduohor.events.ServerEvent
import com.doduohor.events.ServerEventType
import com.doduohor.events.toEventPayload
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.MonitoringTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

class MonitoringService(
    private val measurementService: MeasurementService,
    private val incidentService: IncidentService,
    private val equipmentRepository: EquipmentRepository,
    private val incidentPolicy: IncidentPolicy,
    private val eventPublisher: EventPublisher,
    private val outboxEventRepository: OutboxEventsRepository,
    private val monitoringTransaction: MonitoringTransaction,
    private val clock: Clock
) {
    suspend fun processMeasurement(
        equipmentId: Long,
        type: MeasurementType,
        unit: MeasurementUnit,
        value: Double
    ): MonitoringServiceResult {
        val transactionResult = try {
            monitoringTransaction.execute {
                val measurement = when (val measurementResult = measurementService.create(equipmentId, type, unit, value)) {
                    is CreateMeasurementResult.Success -> measurementResult.measurement
                    else -> return@execute MonitoringTransactionResult(
                        result = MonitoringServiceResult.MeasurementCreateError(measurementResult),
                        events = emptyList()
                    )
                }

                val measurementPayload = Json.encodeToJsonElement(measurement.toEventPayload())
                val measurementEventCreatedAt = clock.now()
                saveOutboxEvent(
                    NewOutboxEvents.create(
                        eventId = Uuid.random(),
                        eventType = IntegrationEventType.MEASUREMENT_CREATED,
                        payload = measurementPayload,
                        createdAt = OffsetDateTime.ofInstant(measurementEventCreatedAt, ZoneOffset.UTC)
                    )
                )

                val result = when (val policyResult = incidentPolicy.detect(measurement)) {
                    is IncidentPolicyResult.NeedIncident -> createIncident(measurement, policyResult.incidentRequired)
                    IncidentPolicyResult.NotIncident -> MonitoringServiceResult.SuccessWithoutIncident(measurement)
                }

                val events = buildList {
                    add(ServerEvent(ServerEventType.MEASUREMENT_CREATED, measurementPayload, measurementEventCreatedAt))
                    if (result is MonitoringServiceResult.SuccessWithIncident) {
                        val incidentPayload = Json.encodeToJsonElement(result.incident.toEventPayload())
                        val incidentEventCreatedAt = clock.now()
                        saveOutboxEvent(
                            NewOutboxEvents.create(
                                eventId = Uuid.random(),
                                eventType = IntegrationEventType.INCIDENT_CREATED,
                                payload = incidentPayload,
                                createdAt = OffsetDateTime.ofInstant(incidentEventCreatedAt, ZoneOffset.UTC)
                            )
                        )
                        add(ServerEvent(ServerEventType.INCIDENT_CREATED, incidentPayload, incidentEventCreatedAt))
                    }
                }

                MonitoringTransactionResult(result = result, events = events)
            }
        } catch (exception: OutboxPersistenceException) {
            return MonitoringServiceResult.OutboxPersistenceError(exception.message ?: "Unable to save outbox event")
        }

        for (event in transactionResult.events) {
            eventPublisher.publish(event)
        }
        return transactionResult.result
    }

    private fun saveOutboxEvent(event: NewOutboxEvents) {
        if (outboxEventRepository.saveEvent(event) == SaveEventResult.Error) {
            throw OutboxPersistenceException()
        }
    }

    private fun createIncident(measurement: Measurement, incidentRequired: IncidentRequired): MonitoringServiceResult {
        val equipment = equipmentRepository.findByEquipmentId(measurement.equipmentId)
            ?: return MonitoringServiceResult.EquipmentContextLost(measurement)
        val incidentResult = incidentService.create(
            facilityId = equipment.facilityId.value,
            equipmentId = equipment.id.value,
            measurementId = measurement.id.value,
            type = incidentRequired.type,
            severity = incidentRequired.severity,
            measurementType = measurement.measurementReading.type,
            measurementUnit = measurement.measurementReading.unit,
            value = measurement.measurementReading.value
        )
        val incident = when (incidentResult) {
            is IncidentServiceResult.Success -> incidentResult.incident
            else -> return MonitoringServiceResult.IncidentCreateError(measurement, incidentResult)
        }
        return MonitoringServiceResult.SuccessWithIncident(measurement, incident)
    }
}

private class OutboxPersistenceException : RuntimeException("Unable to save outbox event")

sealed interface MonitoringServiceResult {
    data class SuccessWithIncident(val measurement: Measurement, val incident: Incident) : MonitoringServiceResult
    data class EquipmentContextLost(val measurement: Measurement) : MonitoringServiceResult
    data class SuccessWithoutIncident(val measurement: Measurement) : MonitoringServiceResult
    data class IncidentCreateError(val measurement: Measurement, val incidentResult: IncidentServiceResult) : MonitoringServiceResult
    data class MeasurementCreateError(val measurementResult: CreateMeasurementResult) : MonitoringServiceResult
    data class OutboxPersistenceError(val reason: String) : MonitoringServiceResult
}
