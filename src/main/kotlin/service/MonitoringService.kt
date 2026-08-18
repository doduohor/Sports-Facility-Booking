package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.Measurement
import com.doduohor.events.EventPublisher
import com.doduohor.events.ServerEvent
import com.doduohor.events.ServerEventType
import com.doduohor.events.toEventPayload
import com.doduohor.infrastructure.messaging.MessagePublisher
import com.doduohor.infrastructure.messaging.RabbitMqEvent
import com.doduohor.infrastructure.messaging.RabbitMqEventType
import com.doduohor.repository.EquipmentRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Instant

class MonitoringService(
    private val measurementService: MeasurementService,
    private val incidentService: IncidentService,
    private val equipmentRepository: EquipmentRepository,
    private val incidentPolicy: IncidentPolicy,
    private val eventPublisher: EventPublisher,
    private val messagePublisher : MessagePublisher
){
    suspend fun processMeasurement(equipmentId: Long, type: String, unit: String, value: Double): MonitoringServiceResult{
        val measurement = when(val measurementResult = measurementService.create(equipmentId, type, unit, value)){
            is CreateMeasurementResult.Success -> measurementResult.measurement
            else -> return MonitoringServiceResult.MeasurementCreateError(measurementResult)
        }
        val event = ServerEvent(
            type = ServerEventType.MEASUREMENT_CREATED,
            data = Json.encodeToJsonElement(measurement.toEventPayload()),
            createdAt = Instant.now()
        )
        eventPublisher.publish(event)
        val rabbitMqEvent = RabbitMqEvent.create(
            eventType = RabbitMqEventType.MEASUREMENT_CREATED,
            data = Json.encodeToJsonElement(measurement.toEventPayload())
        )
        messagePublisher.publish(Json.encodeToString(rabbitMqEvent))

        return when(val incidentPolicy = incidentPolicy.detect(measurement)){
            is IncidentPolicyResult.NeedIncident -> createIncident(measurement, incidentPolicy.incidentRequired)
            IncidentPolicyResult.NotIncident -> MonitoringServiceResult.SuccessWithoutIncident(measurement)
        }
    }

    private suspend fun createIncident(measurement: Measurement, incidentRequired: IncidentRequired): MonitoringServiceResult{
        val equipment = equipmentRepository.findByEquipmentId(measurement.equipmentId) ?:
            return MonitoringServiceResult.EquipmentContextLost(measurement)
        val incidentResult = incidentService.create(
            facilityId = equipment.facilityId,
            equipmentId = equipment.id,
            measurementId = measurement.id,
            type = incidentRequired.type.toString(),
            severity = incidentRequired.severity.toString(),
            measurementType = measurement.type.toString(),
            measurementUnit = measurement.unit.toString(),
            value = measurement.value
        )
        val incident = when(incidentResult){
            is IncidentServiceResult.Success -> incidentResult.incident
            else -> return MonitoringServiceResult.IncidentCreateError(measurement, incidentResult)
        }

        val event = ServerEvent(
            type = ServerEventType.INCIDENT_CREATED,
            data = Json.encodeToJsonElement(incident.toEventPayload()),
            createdAt = Instant.now()
        )
        eventPublisher.publish(event)

        val rabbitMqEvent = RabbitMqEvent.create(
            eventType = RabbitMqEventType.INCIDENT_CREATED,
            data = Json.encodeToJsonElement(incident.toEventPayload())
        )

        messagePublisher.publish(Json.encodeToString(rabbitMqEvent))

        return MonitoringServiceResult.SuccessWithIncident(measurement,incident)

    }
}

sealed interface MonitoringServiceResult{
    data class SuccessWithIncident(val measurement: Measurement,val incident: Incident): MonitoringServiceResult
    data class EquipmentContextLost(val measurement: Measurement): MonitoringServiceResult
    data class SuccessWithoutIncident(val measurement: Measurement): MonitoringServiceResult
    data class IncidentCreateError(val measurement: Measurement, val incidentResult: IncidentServiceResult): MonitoringServiceResult
    data class MeasurementCreateError(val measurementResult: CreateMeasurementResult): MonitoringServiceResult
}