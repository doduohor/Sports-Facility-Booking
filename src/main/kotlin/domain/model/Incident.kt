package com.doduohor.domain.model

import java.time.Instant

enum class IncidentType{
    SMOKE_DETECTED,            // Обнаружено задымление
    HIGH_CO2,                  // Повышенный уровень CO2
    HIGH_TEMPERATURE,          // Повышенная температура
    LOW_TEMPERATURE,           // Пониженная температура
    HIGH_HUMIDITY,             // Повышенная влажность
    LOW_HUMIDITY               // Пониженная влажность
 }

enum class IncidentSeverity{
    MEDIUM,
    HIGH,
    CRITICAL
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
    val id: Long,
    val facilityId: Long,
    val equipmentId: Long,
    val measurementId: Long,
    val type: IncidentType,
    val severity: IncidentSeverity,
    val status: IncidentStatus,
    val measurementType: MeasurementType,
    val measurementUnit: MeasurementUnit,
    val value: Double,
    val createdAt: Instant
)
