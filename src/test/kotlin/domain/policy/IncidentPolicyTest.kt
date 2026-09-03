package com.doduohor.domain.policy

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
import kotlin.test.assertFalse

class IncidentPolicyTest {
    private val policy = IncidentPolicy()

    @Test
    fun `published configuration contains all eighteen rules in six bands`() {
        assertEquals(6, policy.thresholdBands.size)
        assertEquals(18, policy.thresholdBands.sumOf { it.rules.size })
        assertEquals(
            mapOf(
                MeasurementType.TEMPERATURE to 6,
                MeasurementType.HUMIDITY to 6,
                MeasurementType.CO2 to 3,
                MeasurementType.SMOKE to 3
            ),
            policy.thresholdBands
                .flatMap { band -> band.rules.map { band.measurementType } }
                .groupingBy { it }
                .eachCount()
        )
        assertEquals(
            mapOf(
                "temperature-low" to listOf(10.0 to 16.0, 16.0 to 18.0, 18.0 to 20.0),
                "temperature-high" to listOf(25.0 to 28.0, 28.0 to 32.0, 32.0 to 40.0),
                "humidity-low" to listOf(5.0 to 10.0, 10.0 to 15.0, 15.0 to 20.0),
                "humidity-high" to listOf(60.0 to 65.0, 65.0 to 70.0, 70.0 to 90.0),
                "co2" to listOf(5000.0 to 6000.0, 6000.0 to 7000.0, 7000.0 to 8000.0),
                "smoke" to listOf(5.0 to 10.0, 10.0 to 15.0, 15.0 to 50.0)
            ),
            policy.thresholdBands.associate { band ->
                band.name to band.rules.map { it.min to it.max }
            }
        )
    }

    @Test
    fun `continuous chains are joined and do not overlap`() {
        policy.thresholdBands.forEach { band ->
            band.rules.zipWithNext().forEach { (previous, next) ->
                assertEquals(previous.max, next.min, band.name)
            }
        }

        policy.thresholdBands
            .groupBy { it.measurementType }
            .values
            .forEach { bands ->
                val rules = bands.flatMap { it.rules }
                rules.forEachIndexed { index, first ->
                    rules.drop(index + 1).forEach { second ->
                        assertFalse(first.min < second.max && second.min < first.max)
                    }
                }
            }
    }

    @Test
    fun `detects every rule at lower and nearest interior boundaries`() {
        expectedRules.forEach { expected ->
            assertEquals(expected.result, policy.detect(measurement(expected.type, expected.min)))
            assertEquals(expected.result, policy.detect(measurement(expected.type, Math.nextUp(expected.min))))
            assertEquals(expected.result, policy.detect(measurement(expected.type, Math.nextDown(expected.max))))
        }
    }

    @Test
    fun `nearest values outside each rule resolve to the adjacent rule or normal`() {
        expectedRules.forEachIndexed { index, expected ->
            val previous = expectedRules.getOrNull(index - 1)
                ?.takeIf { it.type == expected.type && it.max == expected.min }
            val next = expectedRules.getOrNull(index + 1)
                ?.takeIf { it.type == expected.type && it.min == expected.max }

            assertEquals(
                previous?.result ?: IncidentPolicyResult.NotIncident,
                policy.detect(measurement(expected.type, Math.nextDown(expected.min)))
            )
            assertEquals(
                next?.result ?: IncidentPolicyResult.NotIncident,
                policy.detect(measurement(expected.type, Math.nextUp(expected.max)))
            )
        }
    }

    @Test
    fun `upper boundary belongs to the next rule or is normal`() {
        expectedRules.forEachIndexed { index, expected ->
            val next = expectedRules.getOrNull(index + 1)
                ?.takeIf { it.type == expected.type && it.min == expected.max }
            assertEquals(
                next?.result ?: IncidentPolicyResult.NotIncident,
                policy.detect(measurement(expected.type, expected.max))
            )
        }
    }

    @Test
    fun `intentional normal gaps and safe closed boundaries are not incidents`() {
        listOf(
            MeasurementType.TEMPERATURE to 20.0,
            MeasurementType.TEMPERATURE to Math.nextUp(20.0),
            MeasurementType.TEMPERATURE to Math.nextDown(25.0),
            MeasurementType.HUMIDITY to 20.0,
            MeasurementType.HUMIDITY to Math.nextDown(60.0),
            MeasurementType.CO2 to Math.nextDown(5000.0),
            MeasurementType.CO2 to 8000.0,
            MeasurementType.CO2 to 10000.0,
            MeasurementType.CO2 to 9000.0,
            MeasurementType.SMOKE to Math.nextDown(5.0),
            MeasurementType.SMOKE to 50.0,
            MeasurementType.SMOKE to 100.0,
            MeasurementType.SMOKE to 75.0
        ).forEach { (type, value) ->
            assertEquals(IncidentPolicyResult.NotIncident, policy.detect(measurement(type, value)))
        }
    }

    @Test
    fun `non finite values are safe at policy boundary`() {
        MeasurementType.entries.forEach { type ->
            listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
                assertFailsWith<IllegalArgumentException> {
                    MeasurementReading(type, unitFor(type), value)
                }
                policy.thresholdBands
                    .filter { it.measurementType == type }
                    .flatMap { it.rules }
                    .forEach { rule -> assertFalse(rule.contains(value)) }
            }
        }
    }

    @Test
    fun `invalid rules fail fast`() {
        assertFailsWith<IllegalArgumentException> { IncidentRule(IncidentType.HIGH_CO2, 1.0, 1.0, IncidentSeverity.MEDIUM) }
        assertFailsWith<IllegalArgumentException> { IncidentRule(IncidentType.HIGH_CO2, 2.0, 1.0, IncidentSeverity.MEDIUM) }
        assertFailsWith<IllegalArgumentException> { IncidentRule(IncidentType.HIGH_CO2, Double.NaN, 2.0, IncidentSeverity.MEDIUM) }
        assertFailsWith<IllegalArgumentException> { IncidentRule(IncidentType.HIGH_CO2, 1.0, Double.POSITIVE_INFINITY, IncidentSeverity.MEDIUM) }
    }

    @Test
    fun `invalid configuration rejects overlap, unsorted rules and chain gaps`() {
        assertFailsWith<IllegalArgumentException> {
            IncidentPolicy(listOf(ThresholdBand("co2", MeasurementType.CO2, listOf(rule(1.0, 3.0), rule(2.0, 4.0)))))
        }
        assertFailsWith<IllegalArgumentException> {
            IncidentPolicy(listOf(ThresholdBand("co2", MeasurementType.CO2, listOf(rule(2.0, 3.0), rule(1.0, 2.0)))))
        }
        assertFailsWith<IllegalArgumentException> {
            IncidentPolicy(listOf(ThresholdBand("co2", MeasurementType.CO2, listOf(rule(1.0, 2.0), rule(3.0, 4.0)))))
        }
    }

    private fun measurement(type: MeasurementType, value: Double): Measurement =
        Measurement.create(MeasurementId(1), EquipmentId(1), MeasurementReading(type, unitFor(type), value), Instant.EPOCH)

    private fun rule(min: Double, max: Double) =
        IncidentRule(IncidentType.HIGH_CO2, min, max, IncidentSeverity.MEDIUM)

    private fun unitFor(type: MeasurementType) = when (type) {
        MeasurementType.TEMPERATURE -> MeasurementUnit.CELSIUS
        MeasurementType.HUMIDITY, MeasurementType.SMOKE -> MeasurementUnit.PERCENT
        MeasurementType.CO2 -> MeasurementUnit.PPM
    }

    private data class ExpectedRule(
        val type: MeasurementType,
        val min: Double,
        val max: Double,
        val result: IncidentPolicyResult.NeedIncident
    )

    private fun incident(type: IncidentType, severity: IncidentSeverity) =
        IncidentPolicyResult.NeedIncident(IncidentRequired(type, severity))

    private val expectedRules = listOf(
        ExpectedRule(MeasurementType.TEMPERATURE, 10.0, 16.0, incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.CRITICAL)),
        ExpectedRule(MeasurementType.TEMPERATURE, 16.0, 18.0, incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.HIGH)),
        ExpectedRule(MeasurementType.TEMPERATURE, 18.0, 20.0, incident(IncidentType.LOW_TEMPERATURE, IncidentSeverity.MEDIUM)),
        ExpectedRule(MeasurementType.TEMPERATURE, 25.0, 28.0, incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.MEDIUM)),
        ExpectedRule(MeasurementType.TEMPERATURE, 28.0, 32.0, incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.HIGH)),
        ExpectedRule(MeasurementType.TEMPERATURE, 32.0, 40.0, incident(IncidentType.HIGH_TEMPERATURE, IncidentSeverity.CRITICAL)),
        ExpectedRule(MeasurementType.HUMIDITY, 5.0, 10.0, incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.CRITICAL)),
        ExpectedRule(MeasurementType.HUMIDITY, 10.0, 15.0, incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.HIGH)),
        ExpectedRule(MeasurementType.HUMIDITY, 15.0, 20.0, incident(IncidentType.LOW_HUMIDITY, IncidentSeverity.MEDIUM)),
        ExpectedRule(MeasurementType.HUMIDITY, 60.0, 65.0, incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.MEDIUM)),
        ExpectedRule(MeasurementType.HUMIDITY, 65.0, 70.0, incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.HIGH)),
        ExpectedRule(MeasurementType.HUMIDITY, 70.0, 90.0, incident(IncidentType.HIGH_HUMIDITY, IncidentSeverity.CRITICAL)),
        ExpectedRule(MeasurementType.CO2, 5000.0, 6000.0, incident(IncidentType.HIGH_CO2, IncidentSeverity.MEDIUM)),
        ExpectedRule(MeasurementType.CO2, 6000.0, 7000.0, incident(IncidentType.HIGH_CO2, IncidentSeverity.HIGH)),
        ExpectedRule(MeasurementType.CO2, 7000.0, 8000.0, incident(IncidentType.HIGH_CO2, IncidentSeverity.CRITICAL)),
        ExpectedRule(MeasurementType.SMOKE, 5.0, 10.0, incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.MEDIUM)),
        ExpectedRule(MeasurementType.SMOKE, 10.0, 15.0, incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH)),
        ExpectedRule(MeasurementType.SMOKE, 15.0, 50.0, incident(IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL))
    )
}
