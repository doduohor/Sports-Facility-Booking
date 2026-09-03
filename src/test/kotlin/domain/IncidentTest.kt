package com.doduohor.domain

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentStatus
import com.doduohor.domain.model.IncidentTransitionResult
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.IncidentId
import com.doduohor.domain.shared.MeasurementId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class IncidentTest {
    private val createdAt = Instant.parse("2026-08-29T08:00:00Z")
    private val transitionAt = Instant.parse("2026-08-29T09:00:00Z")

    @Test
    fun `incident exposes no public constructor`() {
        assertFalse(Incident::class.constructors.any { it.visibility == kotlin.reflect.KVisibility.PUBLIC })
        assertFalse(Incident::class.members.any { it.name == "copy" })
    }

    @Test
    fun `create new incident starts open at creation time`() {
        val result = Incident.createNew(
            incidentId = IncidentId(1),
            facilityId = FacilityId(2),
            equipmentId = EquipmentId(3),
            measurementId = MeasurementId(4),
            type = IncidentType.HIGH_TEMPERATURE,
            severity = IncidentSeverity.HIGH,
            measurementType = MeasurementType.TEMPERATURE,
            measurementUnit = MeasurementUnit.CELSIUS,
            value = 35.0,
            createdAt = createdAt
        )

        val incident = assertIs<com.doduohor.domain.model.IncidentCreationResult.Success<Incident>>(result).value
        assertEquals(IncidentStatus.OPEN, incident.status)
        assertEquals(createdAt, incident.statusChangedAt)
    }

    @Test
    fun `restore preserves persisted lifecycle state`() {
        val incident = incidentWithStatus(IncidentStatus.RESOLVED, transitionAt)

        assertEquals(IncidentStatus.RESOLVED, incident.status)
        assertEquals(transitionAt, incident.statusChangedAt)
    }

    @Test
    fun `open incident can be taken in progress`() {
        val incident = incidentWithStatus(IncidentStatus.OPEN)

        val result = incident.startProgress(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.IN_PROGRESS)
    }

    @Test
    fun `open incident can be marked false positive`() {
        val incident = incidentWithStatus(IncidentStatus.OPEN)

        val result = incident.markFalsePositive(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.FALSE_POSITIVE)
    }

    @Test
    fun `in progress incident can be resolved`() {
        val incident = incidentWithStatus(IncidentStatus.IN_PROGRESS)

        val result = incident.resolve(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.RESOLVED)
    }

    @Test
    fun `in progress incident can be marked false positive`() {
        val incident = incidentWithStatus(IncidentStatus.IN_PROGRESS)

        val result = incident.markFalsePositive(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.FALSE_POSITIVE)
    }

    @Test
    fun `resolved incident can be closed`() {
        val incident = incidentWithStatus(IncidentStatus.RESOLVED)

        val result = incident.close(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.CLOSED)
    }

    @Test
    fun `resolved incident can be marked false positive`() {
        val incident = incidentWithStatus(IncidentStatus.RESOLVED)

        val result = incident.markFalsePositive(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.FALSE_POSITIVE)
    }

    @Test
    fun `resolved incident can be reopened`() {
        val incident = incidentWithStatus(IncidentStatus.RESOLVED)

        val result = incident.reopen(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.REOPENED)
    }

    @Test
    fun `closed incident can be reopened`() {
        val incident = incidentWithStatus(IncidentStatus.CLOSED)

        val result = incident.reopen(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.REOPENED)
    }

    @Test
    fun `false positive incident can be reopened`() {
        val incident = incidentWithStatus(IncidentStatus.FALSE_POSITIVE)

        val result = incident.reopen(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.REOPENED)
    }

    @Test
    fun `reopened incident can be marked false positive`() {
        val incident = incidentWithStatus(IncidentStatus.REOPENED)

        val result = incident.markFalsePositive(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.FALSE_POSITIVE)
    }

    @Test
    fun `reopened incident can be taken in progress`() {
        val incident = incidentWithStatus(IncidentStatus.REOPENED)

        val result = incident.startProgress(transitionAt)

        assertSuccessfulTransition(result, IncidentStatus.IN_PROGRESS)
    }

    @Test
    fun `incident transitions return the expected result without changing incident on rejection`() {
        val statuses = IncidentStatus.entries

        statuses.forEach { status ->
            val attempts = listOf(
                TransitionAttempt("startProgress") { it.startProgress(transitionAt) },
                TransitionAttempt("markFalsePositive") { it.markFalsePositive(transitionAt) },
                TransitionAttempt("resolve") { it.resolve(transitionAt) },
                TransitionAttempt("close") { it.close(transitionAt) },
                TransitionAttempt("reopen") { it.reopen(transitionAt) }
            )

            attempts.forEach { attempt ->
                val incident = incidentWithStatus(status)
                val result = attempt(incident)
                val expected = expectedResultFor(status, attempt.name)

                assertExpectedResult(result, expected)

                if (expected !is ExpectedTransitionResult.Success) {
                    assertEquals(status, incident.status)
                    assertEquals(createdAt, incident.statusChangedAt)
                }
            }
        }
    }

    @Test
    fun `successful transition stores transition time and preserves incident data`() {
        val incident = incidentWithStatus(IncidentStatus.OPEN)

        val result = assertIs<IncidentTransitionResult.Success>(incident.startProgress(transitionAt))

        assertEquals(transitionAt, result.incident.statusChangedAt)
        assertEquals(incident.id, result.incident.id)
        assertEquals(incident.facilityId, result.incident.facilityId)
        assertEquals(incident.equipmentId, result.incident.equipmentId)
        assertEquals(incident.measurementId, result.incident.measurementId)
        assertEquals(incident.type, result.incident.type)
        assertEquals(incident.severity, result.incident.severity)
        assertEquals(incident.measurementType, result.incident.measurementType)
        assertEquals(incident.measurementUnit, result.incident.measurementUnit)
        assertEquals(incident.value, result.incident.value)
        assertEquals(incident.createdAt, result.incident.createdAt)
    }

    private fun assertSuccessfulTransition(
        result: IncidentTransitionResult,
        expectedStatus: IncidentStatus
    ) {
        val success = assertIs<IncidentTransitionResult.Success>(result)
        assertEquals(expectedStatus, success.incident.status)
        assertEquals(transitionAt, success.incident.statusChangedAt)
    }

    private fun assertExpectedResult(
        result: IncidentTransitionResult,
        expected: ExpectedTransitionResult
    ) {
        when (expected) {
            is ExpectedTransitionResult.Success ->
                assertSuccessfulTransition(result, expected.status)

            ExpectedTransitionResult.InvalidStatus ->
                assertIs<IncidentTransitionResult.InvalidStatus>(result)

            ExpectedTransitionResult.AlreadyInProgress ->
                assertIs<IncidentTransitionResult.AlreadyInProgress>(result)

            ExpectedTransitionResult.AlreadyInFalsePositive ->
                assertIs<IncidentTransitionResult.AlreadyInFalsePositive>(result)

            ExpectedTransitionResult.AlreadyResolved ->
                assertIs<IncidentTransitionResult.AlreadyResolved>(result)

            ExpectedTransitionResult.AlreadyClosed ->
                assertIs<IncidentTransitionResult.AlreadyClosed>(result)

            ExpectedTransitionResult.AlreadyReopen ->
                assertIs<IncidentTransitionResult.AlreadyReopen>(result)
        }
    }

    private fun expectedResultFor(
        status: IncidentStatus,
        operation: String
    ): ExpectedTransitionResult = when (status) {
        IncidentStatus.OPEN -> mapOf(
            "startProgress" to ExpectedTransitionResult.Success(IncidentStatus.IN_PROGRESS),
            "markFalsePositive" to ExpectedTransitionResult.Success(IncidentStatus.FALSE_POSITIVE)
        )

        IncidentStatus.IN_PROGRESS -> mapOf(
            "startProgress" to ExpectedTransitionResult.AlreadyInProgress,
            "resolve" to ExpectedTransitionResult.Success(IncidentStatus.RESOLVED),
            "markFalsePositive" to ExpectedTransitionResult.Success(IncidentStatus.FALSE_POSITIVE)
        )

        IncidentStatus.RESOLVED -> mapOf(
            "resolve" to ExpectedTransitionResult.AlreadyResolved,
            "close" to ExpectedTransitionResult.Success(IncidentStatus.CLOSED),
            "markFalsePositive" to ExpectedTransitionResult.Success(IncidentStatus.FALSE_POSITIVE),
            "reopen" to ExpectedTransitionResult.Success(IncidentStatus.REOPENED)
        )

        IncidentStatus.CLOSED -> mapOf(
            "close" to ExpectedTransitionResult.AlreadyClosed,
            "reopen" to ExpectedTransitionResult.Success(IncidentStatus.REOPENED)
        )

        IncidentStatus.FALSE_POSITIVE -> mapOf(
            "markFalsePositive" to ExpectedTransitionResult.AlreadyInFalsePositive,
            "reopen" to ExpectedTransitionResult.Success(IncidentStatus.REOPENED)
        )

        IncidentStatus.REOPENED -> mapOf(
            "startProgress" to ExpectedTransitionResult.Success(IncidentStatus.IN_PROGRESS),
            "markFalsePositive" to ExpectedTransitionResult.Success(IncidentStatus.FALSE_POSITIVE),
            "reopen" to ExpectedTransitionResult.AlreadyReopen
        )
    }[operation] ?: ExpectedTransitionResult.InvalidStatus

    private sealed interface ExpectedTransitionResult {
        data class Success(val status: IncidentStatus) : ExpectedTransitionResult
        data object InvalidStatus : ExpectedTransitionResult
        data object AlreadyInProgress : ExpectedTransitionResult
        data object AlreadyInFalsePositive : ExpectedTransitionResult
        data object AlreadyResolved : ExpectedTransitionResult
        data object AlreadyClosed : ExpectedTransitionResult
        data object AlreadyReopen : ExpectedTransitionResult
    }

    private data class TransitionAttempt(
        val name: String,
        val invoke: (Incident) -> IncidentTransitionResult
    ) {
        operator fun invoke(incident: Incident): IncidentTransitionResult = invoke.invoke(incident)
    }

    private fun incidentWithStatus(status: IncidentStatus, statusChangedAt: Instant = createdAt): Incident = Incident.restore(
        id = IncidentId(1),
        facilityId = FacilityId(2),
        equipmentId = EquipmentId(3),
        measurementId = MeasurementId(4),
        type = IncidentType.HIGH_TEMPERATURE,
        severity = IncidentSeverity.HIGH,
        status = status,
        measurementType = MeasurementType.TEMPERATURE,
        measurementUnit = MeasurementUnit.CELSIUS,
        value = 35.0,
        createdAt = createdAt,
        statusChangedAt = statusChangedAt
    )
}
