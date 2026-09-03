package com.doduohor.domain.model

import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.MeasurementId
import com.doduohor.domain.shared.IncidentId
import java.time.Instant

enum class IncidentType{
    SMOKE_DETECTED,            // Обнаружено задымление
    HIGH_CO2,                  // Повышенный уровень CO2
    HIGH_TEMPERATURE,          // Повышенная температура
    LOW_TEMPERATURE,           // Пониженная температура
    HIGH_HUMIDITY,             // Повышенная влажность
    LOW_HUMIDITY;              // Пониженная влажность

    companion object {
        fun fromString(value: String): IncidentType? =
            entries.firstOrNull { it.name.equals(value.trim(), true) }
    }
 }

enum class IncidentSeverity{
    MEDIUM,
    HIGH,
    CRITICAL;

    companion object {
        fun fromString(value: String): IncidentSeverity? =
            entries.firstOrNull { it.name.equals(value.trim(), true) }
    }
}

enum class IncidentStatus{
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    FALSE_POSITIVE,
    REOPENED
}

class Incident private constructor(
    val id: IncidentId,
    val facilityId: FacilityId,
    val equipmentId: EquipmentId,
    val measurementId: MeasurementId,
    val type: IncidentType,
    val severity: IncidentSeverity,
    val status: IncidentStatus,
    val measurementType: MeasurementType,
    val measurementUnit: MeasurementUnit,
    val value: Double,
    val createdAt: Instant,
    val statusChangedAt: Instant
) {
    fun startProgress(transitionAt: Instant): IncidentTransitionResult =
        when (this.status) {
            IncidentStatus.OPEN -> {
                IncidentTransitionResult.Success(
                    incidentChangeStatus(this, IncidentStatus.IN_PROGRESS, transitionAt)
                )
            }

            IncidentStatus.REOPENED -> {
                IncidentTransitionResult.Success(
                    incidentChangeStatus(this, IncidentStatus.IN_PROGRESS, transitionAt)
                )
            }

            IncidentStatus.IN_PROGRESS -> IncidentTransitionResult.AlreadyInProgress
            else -> IncidentTransitionResult.InvalidStatus
        }

    fun markFalsePositive(transitionAt: Instant): IncidentTransitionResult =
        when (this.status) {
            IncidentStatus.FALSE_POSITIVE -> IncidentTransitionResult.AlreadyInFalsePositive
            IncidentStatus.CLOSED -> IncidentTransitionResult.InvalidStatus
            else -> {
                IncidentTransitionResult.Success(
                    incidentChangeStatus(this, IncidentStatus.FALSE_POSITIVE, transitionAt)
                )
            }
        }

    fun resolve(transitionAt: Instant): IncidentTransitionResult =
        when (this.status) {
            IncidentStatus.IN_PROGRESS -> {
                IncidentTransitionResult.Success(
                    incidentChangeStatus(this, IncidentStatus.RESOLVED, transitionAt)
                )
            }

            IncidentStatus.RESOLVED -> IncidentTransitionResult.AlreadyResolved
            else -> IncidentTransitionResult.InvalidStatus
        }

    fun close(transitionAt: Instant): IncidentTransitionResult =
        when (this.status) {
            IncidentStatus.RESOLVED -> {
                IncidentTransitionResult.Success(
                    incidentChangeStatus(this, IncidentStatus.CLOSED, transitionAt)
                )
            }

            IncidentStatus.CLOSED -> IncidentTransitionResult.AlreadyClosed
            else -> IncidentTransitionResult.InvalidStatus
        }

    fun reopen(transitionAt: Instant): IncidentTransitionResult =
        when (this.status) {
            IncidentStatus.OPEN -> IncidentTransitionResult.InvalidStatus
            IncidentStatus.IN_PROGRESS -> IncidentTransitionResult.InvalidStatus
            IncidentStatus.REOPENED -> IncidentTransitionResult.AlreadyReopen
            else -> {
                IncidentTransitionResult.Success(
                    incidentChangeStatus(this, IncidentStatus.REOPENED, transitionAt)
                )
            }
        }

    companion object {
        fun createNew(
        incidentId: IncidentId,
        facilityId: FacilityId,
        equipmentId: EquipmentId,
        measurementId: MeasurementId,
        type: IncidentType,
        severity: IncidentSeverity,
        measurementType: MeasurementType,
        measurementUnit: MeasurementUnit,
        value: Double,
        createdAt: Instant
    ): IncidentCreationResult<Incident> {
        return if(!value.isFinite())
            IncidentCreationResult.InvalidValue
        else
            IncidentCreationResult.Success(
                Incident(
                    id = incidentId,
                    facilityId = facilityId,
                    equipmentId = equipmentId,
                    measurementId = measurementId,
                    type = type,
                    severity = severity,
                    status = IncidentStatus.OPEN,
                    measurementType = measurementType,
                    measurementUnit = measurementUnit,
                    value = value,
                    createdAt = createdAt,
                    statusChangedAt = createdAt
                )
            )
    }

        fun restore(
            id: IncidentId,
            facilityId: FacilityId,
            equipmentId: EquipmentId,
            measurementId: MeasurementId,
            type: IncidentType,
            severity: IncidentSeverity,
            status: IncidentStatus,
            measurementType: MeasurementType,
            measurementUnit: MeasurementUnit,
            value: Double,
            createdAt: Instant,
            statusChangedAt: Instant
        ): Incident {
            require(value.isFinite()) { "Incident value must be finite" }
            return Incident(
                id = id,
                facilityId = facilityId,
                equipmentId = equipmentId,
                measurementId = measurementId,
                type = type,
                severity = severity,
                status = status,
                measurementType = measurementType,
                measurementUnit = measurementUnit,
                value = value,
                createdAt = createdAt,
                statusChangedAt = statusChangedAt
            )
        }
    }

    private fun incidentChangeStatus(incident: Incident, status: IncidentStatus, statusChangedAt: Instant): Incident =
        Incident(
            id = incident.id,
            facilityId = incident.facilityId,
            equipmentId = incident.equipmentId,
            measurementId = incident.measurementId,
            type = incident.type,
            severity = incident.severity,
            status = status,
            measurementType = incident.measurementType,
            measurementUnit = incident.measurementUnit,
            value = incident.value,
            createdAt = incident.createdAt,
            statusChangedAt = statusChangedAt
        )

    override fun equals(other: Any?): Boolean =
        this === other || (other is Incident &&
            id == other.id &&
            facilityId == other.facilityId &&
            equipmentId == other.equipmentId &&
            measurementId == other.measurementId &&
            type == other.type &&
            severity == other.severity &&
            status == other.status &&
            measurementType == other.measurementType &&
            measurementUnit == other.measurementUnit &&
            value == other.value &&
            createdAt == other.createdAt &&
            statusChangedAt == other.statusChangedAt)

    override fun hashCode(): Int = listOf(
        id, facilityId, equipmentId, measurementId, type, severity, status,
        measurementType, measurementUnit, value, createdAt, statusChangedAt
    ).hashCode()

}

sealed interface IncidentTransitionResult{
    data class Success(val incident: Incident): IncidentTransitionResult
    data object InvalidStatus: IncidentTransitionResult
    data object AlreadyInProgress: IncidentTransitionResult
    data object AlreadyInFalsePositive: IncidentTransitionResult
    data object AlreadyResolved: IncidentTransitionResult
    data object AlreadyClosed: IncidentTransitionResult
    data object AlreadyReopen: IncidentTransitionResult
}

sealed interface IncidentCreationResult<out T>{
    data class Success<T>(val value: T): IncidentCreationResult<T>
    data object InvalidValue: IncidentCreationResult<Nothing>
}
