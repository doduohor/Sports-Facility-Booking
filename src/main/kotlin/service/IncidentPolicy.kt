package com.doduohor.service

import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementType

class IncidentPolicy{
    private val incidentRules = mapOf<MeasurementType, List<IncidentRule>>(
            MeasurementType.TEMPERATURE to listOf(
                IncidentRule(
                    incidentType = IncidentType.LOW_TEMPERATURE,
                    min = 18.0,
                    max = 20.0,
                    severity = IncidentSeverity.MEDIUM
                ),
                IncidentRule(
                    incidentType = IncidentType.LOW_TEMPERATURE,
                    min = 16.0,
                    max = 18.0,
                    severity = IncidentSeverity.HIGH
                ),
                IncidentRule(
                    incidentType = IncidentType.LOW_TEMPERATURE,
                    min = 10.0,
                    max = 16.0,
                    severity = IncidentSeverity.CRITICAL
                ),
                IncidentRule(
                    incidentType = IncidentType.HIGH_TEMPERATURE,
                    min = 25.0,
                    max = 28.0,
                    severity = IncidentSeverity.MEDIUM
                ),
                IncidentRule(
                    incidentType = IncidentType.HIGH_TEMPERATURE,
                    min = 28.0,
                    max = 32.0,
                    severity = IncidentSeverity.HIGH
                ),
                IncidentRule(
                    incidentType = IncidentType.HIGH_TEMPERATURE,
                    min = 32.0,
                    max = 40.0,
                    severity = IncidentSeverity.CRITICAL
                )
            ),

            MeasurementType.HUMIDITY to listOf(
                IncidentRule(
                    incidentType = IncidentType.LOW_HUMIDITY,
                    min = 15.0,
                    max = 20.0,
                    severity = IncidentSeverity.MEDIUM
                ),
                IncidentRule(
                    incidentType = IncidentType.LOW_HUMIDITY,
                    min = 10.0,
                    max = 15.0,
                    severity = IncidentSeverity.HIGH
                ),
                IncidentRule(
                    incidentType = IncidentType.LOW_HUMIDITY,
                    min = 5.0,
                    max = 10.0,
                    severity = IncidentSeverity.CRITICAL
                ),
                IncidentRule(
                    incidentType = IncidentType.HIGH_HUMIDITY,
                    min = 60.0,
                    max = 65.0,
                    severity = IncidentSeverity.MEDIUM
                ),
                IncidentRule(
                    incidentType = IncidentType.HIGH_HUMIDITY,
                    min = 65.0,
                    max = 70.0,
                    severity = IncidentSeverity.HIGH
                ),
                IncidentRule(
                    incidentType = IncidentType.HIGH_HUMIDITY,
                    min = 70.0,
                    max = 90.0,
                    severity = IncidentSeverity.CRITICAL
                ),
            ),

            MeasurementType.CO2 to listOf(
                IncidentRule(
                    incidentType = IncidentType.HIGH_CO2,
                    min = 5000.0,
                    max = 6000.0,
                    severity = IncidentSeverity.MEDIUM
                ),
                IncidentRule(
                    incidentType = IncidentType.HIGH_CO2,
                    min = 6000.0,
                    max = 7000.0,
                    severity = IncidentSeverity.HIGH
                ),
                IncidentRule(
                    incidentType = IncidentType.HIGH_CO2,
                    min = 7000.0,
                    max = 8000.0,
                    severity = IncidentSeverity.CRITICAL
                )
            ),

            MeasurementType.SMOKE to listOf(
                IncidentRule(
                    incidentType = IncidentType.SMOKE_DETECTED,
                    min = 5.0,
                    max = 10.0,
                    severity = IncidentSeverity.MEDIUM
                ),
                IncidentRule(
                    incidentType = IncidentType.SMOKE_DETECTED,
                    min = 10.0,
                    max = 15.0,
                    severity = IncidentSeverity.HIGH
                ),
                IncidentRule(
                    incidentType = IncidentType.SMOKE_DETECTED,
                    min = 15.0,
                    max = 50.0,
                    severity = IncidentSeverity.CRITICAL
                )
            )
        )

    fun detect(measurement: Measurement): IncidentPolicyResult{
        val incidentRules = incidentRules[measurement.type] ?: return IncidentPolicyResult.NotIncident
        val incidentRule = incidentRules.find { it.contains(measurement.value)  } ?: return IncidentPolicyResult.NotIncident
        return IncidentPolicyResult.NeedIncident(incidentRule.toRequired())
    }
}

sealed interface IncidentPolicyResult{
    data class NeedIncident(val incidentRequired: IncidentRequired): IncidentPolicyResult
    data object NotIncident: IncidentPolicyResult
}

data class IncidentRequired(val type: IncidentType, val severity: IncidentSeverity)

data class IncidentRule(
    val incidentType: IncidentType,
    val min: Double,
    val max: Double,
    val severity: IncidentSeverity
){
    init{
        require(max > min) {"The maximum value must be greater than the minimum value"}
    }
}

fun IncidentRule.toRequired(): IncidentRequired =
    IncidentRequired(
        type = incidentType,
        severity = severity
    )

fun IncidentRule.contains(value: Double): Boolean =
    value in min..<max