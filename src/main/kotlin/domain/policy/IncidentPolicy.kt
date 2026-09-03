package com.doduohor.domain.policy

import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementType

internal data class IncidentRule(
    val incidentType: IncidentType,
    val min: Double,
    val max: Double,
    val severity: IncidentSeverity,
    val intervalName: String = "${incidentType.name}[$min,$max)"
) {
    init {
        require(min.isFinite() && max.isFinite()) {
            "Threshold ${intervalName} must have finite bounds: [$min,$max)"
        }
        require(min < max) {
            "Threshold ${intervalName} must have min < max: [$min,$max)"
        }
    }
}

internal data class ThresholdBand(
    val name: String,
    val measurementType: MeasurementType,
    val rules: List<IncidentRule>
) {
    init {
        require(name.isNotBlank()) { "Threshold band name must not be blank" }
        require(rules.isNotEmpty()) { "Threshold band $name must not be empty" }
    }
}

class IncidentPolicy internal constructor(
    bands: List<ThresholdBand> = defaultThresholdBands
) {
    internal val thresholdBands: List<ThresholdBand> = bands
        .map { band -> band.copy(rules = band.rules.toList()) }
        .toList()

    private val rulesByMeasurementType: Map<MeasurementType, List<IncidentRule>> =
        thresholdBands
            .flatMap { it.rules.map { rule -> it.measurementType to rule } }
            .groupBy({ it.first }, { it.second })

    init {
        validateBands(thresholdBands)
    }

    fun detect(measurement: Measurement): IncidentPolicyResult {
        val value = measurement.measurementReading.value
        if (!value.isFinite()) return IncidentPolicyResult.NotIncident

        val rule = rulesByMeasurementType[measurement.measurementReading.type]
            ?.firstOrNull { it.contains(value) }
            ?: return IncidentPolicyResult.NotIncident
        return IncidentPolicyResult.NeedIncident(rule.toRequired())
    }
}

sealed interface IncidentPolicyResult {
    data class NeedIncident(val incidentRequired: IncidentRequired) : IncidentPolicyResult
    data object NotIncident : IncidentPolicyResult
}

data class IncidentRequired(val type: IncidentType, val severity: IncidentSeverity)

internal fun IncidentRule.toRequired(): IncidentRequired =
    IncidentRequired(type = incidentType, severity = severity)

internal fun IncidentRule.contains(value: Double): Boolean =
    value.isFinite() && value >= min && value < max

private fun validateBands(bands: List<ThresholdBand>) {
    bands.forEach { band ->
        band.rules.forEach { rule ->
            require(rule.incidentTypeMatches(band.measurementType)) {
                "Threshold ${rule.intervalName} has type ${rule.incidentType}, expected ${band.measurementType}"
            }
        }

        require(band.rules == band.rules.sortedBy { it.min }) {
            val first = band.rules.first()
            "Threshold band ${band.name} is not sorted by min: ${first.intervalName} [${first.min},${first.max})"
        }

    }

    bands.groupBy { it.measurementType }.forEach { (measurementType, groupedBands) ->
        groupedBands.flatMap { it.rules }
            .sortedBy { it.min }
            .zipWithNext()
            .forEach { (previous, next) ->
                require(previous.max <= next.min) {
                    "Overlapping threshold rules for $measurementType: " +
                        "${previous.intervalName} [${previous.min},${previous.max}) and " +
                        "${next.intervalName} [${next.min},${next.max})"
                }
            }
    }

    bands.forEach { band ->
        band.rules.zipWithNext().forEach { (previous, next) ->
            require(previous.max == next.min) {
                "Gap in continuous threshold band ${band.name}: " +
                    "${previous.intervalName} [${previous.min},${previous.max}) and " +
                    "${next.intervalName} [${next.min},${next.max})"
            }
        }
    }
}

private fun IncidentRule.incidentTypeMatches(measurementType: MeasurementType): Boolean =
    when (measurementType) {
        MeasurementType.TEMPERATURE -> incidentType == IncidentType.LOW_TEMPERATURE || incidentType == IncidentType.HIGH_TEMPERATURE
        MeasurementType.HUMIDITY -> incidentType == IncidentType.LOW_HUMIDITY || incidentType == IncidentType.HIGH_HUMIDITY
        MeasurementType.CO2 -> incidentType == IncidentType.HIGH_CO2
        MeasurementType.SMOKE -> incidentType == IncidentType.SMOKE_DETECTED
    }

private val defaultThresholdBands = listOf(
    ThresholdBand(
        "temperature-low", MeasurementType.TEMPERATURE, listOf(
            IncidentRule(IncidentType.LOW_TEMPERATURE, 10.0, 16.0, IncidentSeverity.CRITICAL, "temperature-low-critical"),
            IncidentRule(IncidentType.LOW_TEMPERATURE, 16.0, 18.0, IncidentSeverity.HIGH, "temperature-low-high"),
            IncidentRule(IncidentType.LOW_TEMPERATURE, 18.0, 20.0, IncidentSeverity.MEDIUM, "temperature-low-medium")
        )
    ),
    ThresholdBand(
        "temperature-high", MeasurementType.TEMPERATURE, listOf(
            IncidentRule(IncidentType.HIGH_TEMPERATURE, 25.0, 28.0, IncidentSeverity.MEDIUM, "temperature-high-medium"),
            IncidentRule(IncidentType.HIGH_TEMPERATURE, 28.0, 32.0, IncidentSeverity.HIGH, "temperature-high-high"),
            IncidentRule(IncidentType.HIGH_TEMPERATURE, 32.0, 40.0, IncidentSeverity.CRITICAL, "temperature-high-critical")
        )
    ),
    ThresholdBand(
        "humidity-low", MeasurementType.HUMIDITY, listOf(
            IncidentRule(IncidentType.LOW_HUMIDITY, 5.0, 10.0, IncidentSeverity.CRITICAL, "humidity-low-critical"),
            IncidentRule(IncidentType.LOW_HUMIDITY, 10.0, 15.0, IncidentSeverity.HIGH, "humidity-low-high"),
            IncidentRule(IncidentType.LOW_HUMIDITY, 15.0, 20.0, IncidentSeverity.MEDIUM, "humidity-low-medium")
        )
    ),
    ThresholdBand(
        "humidity-high", MeasurementType.HUMIDITY, listOf(
            IncidentRule(IncidentType.HIGH_HUMIDITY, 60.0, 65.0, IncidentSeverity.MEDIUM, "humidity-high-medium"),
            IncidentRule(IncidentType.HIGH_HUMIDITY, 65.0, 70.0, IncidentSeverity.HIGH, "humidity-high-high"),
            IncidentRule(IncidentType.HIGH_HUMIDITY, 70.0, 90.0, IncidentSeverity.CRITICAL, "humidity-high-critical")
        )
    ),
    ThresholdBand(
        "co2", MeasurementType.CO2, listOf(
            IncidentRule(IncidentType.HIGH_CO2, 5000.0, 6000.0, IncidentSeverity.MEDIUM, "co2-medium"),
            IncidentRule(IncidentType.HIGH_CO2, 6000.0, 7000.0, IncidentSeverity.HIGH, "co2-high"),
            IncidentRule(IncidentType.HIGH_CO2, 7000.0, 8000.0, IncidentSeverity.CRITICAL, "co2-critical")
        )
    ),
    ThresholdBand(
        "smoke", MeasurementType.SMOKE, listOf(
            IncidentRule(IncidentType.SMOKE_DETECTED, 5.0, 10.0, IncidentSeverity.MEDIUM, "smoke-medium"),
            IncidentRule(IncidentType.SMOKE_DETECTED, 10.0, 15.0, IncidentSeverity.HIGH, "smoke-high"),
            IncidentRule(IncidentType.SMOKE_DETECTED, 15.0, 50.0, IncidentSeverity.CRITICAL, "smoke-critical")
        )
    )
)
