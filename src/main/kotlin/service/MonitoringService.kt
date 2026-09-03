package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.policy.IncidentPolicy
import com.doduohor.domain.shared.Clock
import com.doduohor.events.NewOutboxEvents
import com.doduohor.events.OutboxEventWriter
import com.doduohor.events.SaveEventResult
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.MonitoringTransaction

class MonitoringService(
    private val measurementService: MeasurementService,
    private val incidentService: IncidentService,
    private val equipmentRepository: EquipmentRepository,
    private val incidentPolicy: IncidentPolicy,
    private val eventPublisher: com.doduohor.events.ServerEventPublisher,
    private val outboxEventRepository: OutboxEventWriter,
    private val monitoringTransaction: MonitoringTransaction,
    private val clock: Clock,
    private val monitoringEventFactory: MonitoringEventFactory = MonitoringEventFactory(clock)
) {
    private val measurementProcessor = MeasurementProcessor(measurementService)
    private val incidentCoordinator = IncidentCoordinator(incidentPolicy, equipmentRepository, incidentService)

    suspend fun processMeasurement(command: ProcessMeasurementCommand): MonitoringServiceResult {
        val transactionResult = try {
            monitoringTransaction.execute {
                val measurement = when (val measurementResult = measurementProcessor.process(command)) {
                    is MeasurementProcessResult.Success -> measurementResult.measurement
                    is MeasurementProcessResult.Failure -> return@execute MonitoringTransactionResult(
                        result = MonitoringServiceResult.MeasurementCreateError(measurementResult.measurementResult),
                        events = emptyList()
                    )
                }

                val result = when (val coordinationResult = incidentCoordinator.coordinate(measurement)) {
                    IncidentCoordinationResult.NoIncident -> MonitoringServiceResult.SuccessWithoutIncident(measurement)
                    is IncidentCoordinationResult.Created -> MonitoringServiceResult.SuccessWithIncident(
                        measurement,
                        coordinationResult.incident
                    )
                    is IncidentCoordinationResult.EquipmentContextLost -> MonitoringServiceResult.EquipmentContextLost(
                        coordinationResult.measurement
                    )
                    is IncidentCoordinationResult.IncidentCreateError -> MonitoringServiceResult.IncidentCreateError(
                        coordinationResult.measurement,
                        coordinationResult.incidentResult
                    )
                }

                val events = monitoringEventFactory.create(
                    measurement,
                    (result as? MonitoringServiceResult.SuccessWithIncident)?.incident
                )
                events.outboxEvents.forEach(::saveOutboxEvent)

                MonitoringTransactionResult(result = result, events = events.serverEvents)
            }
        } catch (exception: OutboxPersistenceException) {
            return MonitoringServiceResult.OutboxPersistenceError(exception.message ?: "Unable to save outbox event")
        }

        eventPublisher.publish(transactionResult.events)
        return transactionResult.result
    }

    private fun saveOutboxEvent(event: NewOutboxEvents) {
        if (outboxEventRepository.saveEvent(event) == SaveEventResult.Error) {
            throw OutboxPersistenceException()
        }
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
