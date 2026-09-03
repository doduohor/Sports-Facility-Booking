package com.doduohor.service

import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.MeasurementId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IncidentPolicyTest {
    private val policy = IncidentPolicy()

    @Test
    fun `detects every incident rule at its lower boundary and inside its range`() {
        ruleCases.forEach { case ->
            assertEquals(case.expected, policy.detect(measurement(case.type, case.min)))
            assertEquals(case.expected, policy.detect(measurement(case.type, midpoint(case.min, case.max))))
        }
    }

    @Test
    fun `treats every upper boundary as exclusive and checks both sides of each range`() {
        ruleCases.forEach { case ->
            assertEquals(
                case.expectedBelow,
                policy.detect(measurement(case.type, case.min - 0.1))
            )
            assertEquals(
                case.expectedAtUpperBoundary,
                policy.detect(measurement(case.type, case.max))
            )
            assertEquals(
                case.expectedAbove,
                policy.detect(measurement(case.type, case.max + 0.1))
            )
        }
    }

    @Test
    fun `returns not incident for values outside all rules`() {
        notIncidentCases.forEach { (type, value) ->
            assertEquals(IncidentPolicyResult.NotIncident, policy.detect(measurement(type, value)))
        }
    }

    @Test
    fun `rejects rules whose maximum is not greater than minimum`() {
        assertFailsWith<IllegalArgumentException> {
            IncidentRule(IncidentType.HIGH_CO2, min = 1.0, max = 1.0, IncidentSeverity.MEDIUM)
        }
        assertFailsWith<IllegalArgumentException> {
            IncidentRule(IncidentType.HIGH_CO2, min = 2.0, max = 1.0, IncidentSeverity.MEDIUM)
        }
    }

    private fun measurement(type: MeasurementType, value: Double): Measurement =
        Measurement.create(
            id = MeasurementId(1),
            equipmentId = EquipmentId(1),
            measurementReading = MeasurementReading(type, unitFor(type), value),
            createdAt = Instant.parse("2026-08-20T12:00:00Z")
        )

    private fun unitFor(type: MeasurementType): MeasurementUnit = when (type) {
        MeasurementType.TEMPERATURE -> MeasurementUnit.CELSIUS
        MeasurementType.HUMIDITY, MeasurementType.SMOKE -> MeasurementUnit.PERCENT
        MeasurementType.CO2 -> MeasurementUnit.PPM
    }

    private fun midpoint(min: Double, max: Double): Double = min + (max - min) / 2

    private data class RuleCase(
        val type: MeasurementType,
        val min: Double,
        val max: Double,
        val expected: IncidentPolicyResult.NeedIncident,
        val expectedBelow: IncidentPolicyResult,
        val expectedAtUpperBoundary: IncidentPolicyResult,
        val expectedAbove: IncidentPolicyResult
    )

    private fun incident(type: IncidentType, severity: IncidentSeverity) =
        IncidentPolicyResult.NeedIncident(IncidentRequired(type, severity))

    private val notIncident = IncidentPolicyResult.NotIncident

    private val ruleCases = listOf(
        RuleCase(MeasurementType.TEMPERATURE, 18.0, 20.0, incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.MEDIUM), incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.HIGH), notIncident, notIncident),
        RuleCase(MeasurementType.TEMPERATURE, 16.0, 18.0, incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.HIGH), incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.CRITICAL), incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.MEDIUM), incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.MEDIUM)),
        RuleCase(MeasurementType.TEMPERATURE, 10.0, 16.0, incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.CRITICAL), notIncident, incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.HIGH), incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.HIGH)),
        RuleCase(MeasurementType.TEMPERATURE, 25.0, 28.0, incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.MEDIUM), notIncident, incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.HIGH), incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.HIGH)),
        RuleCase(MeasurementType.TEMPERATURE, 28.0, 32.0, incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.HIGH), incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.MEDIUM), incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.CRITICAL), incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.CRITICAL)),
        RuleCase(MeasurementType.TEMPERATURE, 32.0, 40.0, incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.CRITICAL), incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.HIGH), notIncident, notIncident),
        RuleCase(MeasurementType.HUMIDITY, 15.0, 20.0, incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.MEDIUM), incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.HIGH), notIncident, notIncident),
        RuleCase(MeasurementType.HUMIDITY, 10.0, 15.0, incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.HIGH), incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.CRITICAL), incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.MEDIUM), incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.MEDIUM)),
        RuleCase(MeasurementType.HUMIDITY, 5.0, 10.0, incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.CRITICAL), notIncident, incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.HIGH), incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.HIGH)),
        RuleCase(MeasurementType.HUMIDITY, 60.0, 65.0, incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.MEDIUM), notIncident, incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.HIGH), incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.HIGH)),
        RuleCase(MeasurementType.HUMIDITY, 65.0, 70.0, incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.HIGH), incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.MEDIUM), incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.CRITICAL), incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.CRITICAL)),
        RuleCase(MeasurementType.HUMIDITY, 70.0, 90.0, incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.CRITICAL), incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.HIGH), notIncident, notIncident),
        RuleCase(MeasurementType.CO2, 5000.0, 6000.0, incident(IncidentType.HIGH_CO2, IncidentSeverity.MEDIUM), notIncident, incident(IncidentType.HIGH_CO2, IncidentSeverity.HIGH), incident(IncidentType.HIGH_CO2, IncidentSeverity.HIGH)),
        RuleCase(MeasurementType.CO2, 6000.0, 7000.0, incident(IncidentType.HIGH_CO2, IncidentSeverity.HIGH), incident(IncidentType.HIGH_CO2, IncidentSeverity.MEDIUM), incident(IncidentType.HIGH_CO2, IncidentSeverity.CRITICAL), incident(IncidentType.HIGH_CO2, IncidentSeverity.CRITICAL)),
        RuleCase(MeasurementType.CO2, 7000.0, 8000.0, incident(IncidentType.HIGH_CO2, IncidentSeverity.CRITICAL), incident(IncidentType.HIGH_CO2, IncidentSeverity.HIGH), notIncident, notIncident),
        RuleCase(MeasurementType.SMOKE, 5.0, 10.0, incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.MEDIUM), notIncident, incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH), incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH)),
        RuleCase(MeasurementType.SMOKE, 10.0, 15.0, incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH), incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.MEDIUM), incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL), incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL)),
        RuleCase(MeasurementType.SMOKE, 15.0, 50.0, incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL), incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH), notIncident, notIncident)
    )

    private val notIncidentCases = listOf(
        MeasurementType.TEMPERATURE to 9.9,
        MeasurementType.TEMPERATURE to 20.0,
        MeasurementType.TEMPERATURE to 24.9,
        MeasurementType.TEMPERATURE to 40.0,
        MeasurementType.HUMIDITY to 4.9,
        MeasurementType.HUMIDITY to 20.0,
        MeasurementType.HUMIDITY to 59.9,
        MeasurementType.HUMIDITY to 90.0,
        MeasurementType.CO2 to 4999.9,
        MeasurementType.CO2 to 8000.0,
        MeasurementType.SMOKE to 4.9,
        MeasurementType.SMOKE to 50.0
    )
}
