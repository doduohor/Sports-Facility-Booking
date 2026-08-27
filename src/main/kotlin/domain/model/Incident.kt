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

data class Incident(
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
    val createdAt: Instant
)
