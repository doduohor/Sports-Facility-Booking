package com.doduohor.api.mapper

import com.doduohor.api.dto.ErrorResponse
import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityStatus
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentCreationResult
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.BookingId
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.IncidentId
import com.doduohor.domain.shared.MeasurementId
import com.doduohor.service.ActivateFacilityResult
import com.doduohor.service.CreateBookingResult
import com.doduohor.service.CreateEquipmentResult
import com.doduohor.service.CreateFacilityResult
import com.doduohor.service.CreateMeasurementResult
import com.doduohor.service.FindByFacilityResult
import com.doduohor.service.FindEquipmentIdResult
import com.doduohor.service.FindEquipmentsByFacilityIdResult
import com.doduohor.service.FindIncidentsByEquipmentIdResult
import com.doduohor.service.FindIncidentsByFacilityIdResult
import com.doduohor.service.IncidentLifecycleServiceResult
import com.doduohor.service.IncidentServiceResult
import com.doduohor.service.MonitoringServiceResult
import io.ktor.http.HttpStatusCode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiErrorMapperTest {
    @Test
    fun `facility results map every error and success`() {
        assertError(ApiErrorMapper.map(CreateFacilityResult.InvalidName), 400, "invalidName", "Facility name must not be blank")
        assertError(ApiErrorMapper.map(ActivateFacilityResult.InvalidStatus), 409, "invalidStatus", "The object cannot be activated from its current status")
        assertError(ApiErrorMapper.map(ActivateFacilityResult.AlreadyActive), 409, "alreadyActive", "The object is already active")
        assertError(ApiErrorMapper.map(ActivateFacilityResult.NotFound), 404, "Error", "Not Found")
        assertNull(ApiErrorMapper.map(CreateFacilityResult.Success(facility)))
        assertNull(ApiErrorMapper.map(ActivateFacilityResult.Success(facility)))
    }

    @Test
    fun `booking results map every error and success`() {
        val createCases: List<Pair<CreateBookingResult, ErrorResponse>> = listOf(
            CreateBookingResult.InvalidFacilityId to ErrorResponse(400, "invalidFacilityId", "The Facility ID must be positive."),
            CreateBookingResult.InvalidCustomerId to ErrorResponse(400, "invalidCustomerId", "Customer ID is not allowed to create bookings."),
            CreateBookingResult.InvalidTimeInterval to ErrorResponse(400, "invalidTimeInterval", "The time interval must be between 1 and 12 hours."),
            CreateBookingResult.NotFindFacilityId to ErrorResponse(404, "notFindFacilityId", "The specified facilityId was not found."),
            CreateBookingResult.InvalidStatusFacilityId to ErrorResponse(409, "invalidStatusFacilityId", "The status of this facility does not allow bookings to be created."),
            CreateBookingResult.UnavailableRangeTimeLimit to ErrorResponse(409, "unavailableRangeTimeLimit", "The specified time slot is partially or fully booked.")
        )
        createCases.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        val findCases: List<Pair<FindByFacilityResult, ErrorResponse>> = listOf(
            FindByFacilityResult.InvalidFacilityId to ErrorResponse(400, "invalidFacilityId", "You entered an incorrect facilityId"),
            FindByFacilityResult.NotFindFacilityId to ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
        )
        findCases.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        assertNull(ApiErrorMapper.map(CreateBookingResult.Success(booking)))
        assertNull(ApiErrorMapper.map(FindByFacilityResult.Success(listOf(booking))))
    }

    @Test
    fun `equipment results map every error`() {
        val createCases: List<Pair<CreateEquipmentResult, ErrorResponse>> = listOf(
            CreateEquipmentResult.InvalidFacilityId to ErrorResponse(400, "invalidFacilityId", "You entered an incorrect facilityId"),
            CreateEquipmentResult.NotFindFacilityId to ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist"),
            CreateEquipmentResult.InvalidName to ErrorResponse(400, "invalidName", "An incorrect name has been specified")
        )
        createCases.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        val findCases: List<Pair<FindEquipmentsByFacilityIdResult, ErrorResponse>> = listOf(
            FindEquipmentsByFacilityIdResult.InvalidFacilityId to ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified"),
            FindEquipmentsByFacilityIdResult.NotFindFacilityId to ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
        )
        findCases.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        assertNull(ApiErrorMapper.map(CreateEquipmentResult.Success(equipment)))
        assertNull(ApiErrorMapper.map(FindEquipmentsByFacilityIdResult.Success(listOf(equipment))))
    }

    @Test
    fun `measurement and monitoring results map every error branch`() {
        val creationErrors: List<Pair<CreateMeasurementResult, ErrorResponse>> = listOf(
            CreateMeasurementResult.InvalidEquipmentId to ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified"),
            CreateMeasurementResult.NotFindEquipmentId to ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist"),
            CreateMeasurementResult.InvalidMappingTypeAndUnit to ErrorResponse(400, "invalidMappingTypeAndUnit", "The measurement type does not match the specified unit"),
            CreateMeasurementResult.NotSupportedEquipmentType to ErrorResponse(500, "notSupportedEquipmentType", "Measurement rules are not configured for this equipment type"),
            CreateMeasurementResult.InvalidMeasurementType to ErrorResponse(409, "invalidMeasurementType", "This measurement type is not supported by the specified equipment"),
            CreateMeasurementResult.InvalidValue to ErrorResponse(400, "invalidValue", "The measurement value is outside the allowed range"),
            CreateMeasurementResult.MeasurementRangeNotConfigured to ErrorResponse(500, "measurementRangeNotConfigured", "Measurement value range is not configured"),
            CreateMeasurementResult.NotFindMeasurementType to ErrorResponse(404, "notFindMeasurementType", "The specified Measurement Type does not exist")
        )
        creationErrors.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        val findErrors: List<Pair<FindEquipmentIdResult, ErrorResponse>> = listOf(
            FindEquipmentIdResult.InvalidEquipmentId to ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified"),
            FindEquipmentIdResult.NotFindEquipmentId to ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
        )
        findErrors.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        assertNull(ApiErrorMapper.map(CreateMeasurementResult.Success(measurement)))
        assertNull(ApiErrorMapper.map(FindEquipmentIdResult.Success(listOf(measurement))))

        assertNull(ApiErrorMapper.map(MonitoringServiceResult.SuccessWithoutIncident(measurement)))
        assertNull(ApiErrorMapper.map(MonitoringServiceResult.SuccessWithIncident(measurement, incident)))
        assertNull(ApiErrorMapper.map(MonitoringServiceResult.MeasurementCreateError(CreateMeasurementResult.Success(measurement))))
        assertError(ApiErrorMapper.map(MonitoringServiceResult.EquipmentContextLost(measurement)), 500, "equipmentContextLost", "Equipment context was not found after measurement creation")
        assertError(ApiErrorMapper.map(MonitoringServiceResult.IncidentCreateError(measurement, IncidentServiceResult.InvalidValue)), 500, "incidentCreateError", "Measurement was created, but incident creation failed")
        assertError(ApiErrorMapper.map(MonitoringServiceResult.OutboxPersistenceError("ignored")), 500, "outboxPersistenceError", "Measurement event could not be stored")
    }

    @Test
    fun `incident results map every error branch`() {
        val serviceCases: List<Pair<IncidentServiceResult, ErrorResponse>> = listOf(
            IncidentServiceResult.InvalidFacilityId to ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified"),
            IncidentServiceResult.InvalidEquipmentId to ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified"),
            IncidentServiceResult.InvalidMeasurementId to ErrorResponse(400, "invalidMeasurementId", "An incorrect Measurement ID has been specified"),
            IncidentServiceResult.NotFindFacilityId to ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist"),
            IncidentServiceResult.NotFindEquipmentId to ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist"),
            IncidentServiceResult.EquipmentDoesNotBelongToFacility to ErrorResponse(409, "equipmentDoesNotBelongToFacility", "The equipment does not belong to the specified facility"),
            IncidentServiceResult.InvalidValue to ErrorResponse(400, "invalidValue", "An incorrect incident value has been specified")
        )
        serviceCases.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        val facilityCases: List<Pair<FindIncidentsByFacilityIdResult, ErrorResponse>> = listOf(
            FindIncidentsByFacilityIdResult.InvalidFacilityId to ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified"),
            FindIncidentsByFacilityIdResult.NotFindFacilityId to ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist"),
        )
        facilityCases.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        val equipmentCases: List<Pair<FindIncidentsByEquipmentIdResult, ErrorResponse>> = listOf(
            FindIncidentsByEquipmentIdResult.InvalidEquipmentId to ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified"),
            FindIncidentsByEquipmentIdResult.NotFindEquipmentId to ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
        )
        equipmentCases.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        val lifecycleCases: List<Pair<IncidentLifecycleServiceResult, ErrorResponse>> = listOf(
            IncidentLifecycleServiceResult.InvalidIncidentId to ErrorResponse(400, "invalidIncidentId", "An incorrect Incident ID has been specified"),
            IncidentLifecycleServiceResult.InvalidStatus to ErrorResponse(409, "invalidStatus", "The incident cannot be transitioned from its current status"),
            IncidentLifecycleServiceResult.AlreadyInProgress to ErrorResponse(409, "alreadyInProgress", "The incident is already in progress"),
            IncidentLifecycleServiceResult.AlreadyInFalsePositive to ErrorResponse(409, "alreadyInFalsePositive", "The incident is already marked as false positive"),
            IncidentLifecycleServiceResult.AlreadyResolved to ErrorResponse(409, "alreadyResolved", "The incident is already resolved"),
            IncidentLifecycleServiceResult.AlreadyClosed to ErrorResponse(409, "alreadyClosed", "The incident is already closed"),
            IncidentLifecycleServiceResult.AlreadyReopen to ErrorResponse(409, "alreadyReopen", "The incident is already reopened"),
            IncidentLifecycleServiceResult.StatusUpdateError to ErrorResponse(500, "statusUpdateError", "The incident status could not be updated"),
            IncidentLifecycleServiceResult.NotFindIncidentId to ErrorResponse(404, "notFindIncidentId", "The specified Incident ID does not exist")
        )
        lifecycleCases.forEach { (result, expected) -> assertMapped(ApiErrorMapper.map(result), expected) }
        assertNull(ApiErrorMapper.map(IncidentServiceResult.Success(incident)))
        assertNull(ApiErrorMapper.map(FindIncidentsByFacilityIdResult.Success(listOf(incident))))
        assertNull(ApiErrorMapper.map(FindIncidentsByEquipmentIdResult.Success(listOf(incident))))
        assertNull(ApiErrorMapper.map(IncidentLifecycleServiceResult.Success(incident)))
    }

    private fun assertError(actual: ApiError?, code: Int, name: String, text: String) {
        assertEquals(ApiError(HttpStatusCode.fromValue(code), ErrorResponse(code, name, text)), actual)
    }

    private fun assertMapped(actual: ApiError?, expected: ErrorResponse) {
        assertEquals(HttpStatusCode.fromValue(expected.code), actual?.status)
        assertEquals(expected, actual?.response)
    }

    private companion object {
        val facility = Facility(FacilityId(1), "Gym", FacilityType.GYM, FacilityStatus.ACTIVE)
        val equipment = Equipment(EquipmentId(1), FacilityId(1), "Boiler", EquipmentType.HEATING, EquipmentStatus.ACTIVE)
        val measurement = Measurement.create(MeasurementId(1), EquipmentId(1), MeasurementReading(MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 20.0), Instant.EPOCH)
        val booking = Booking.createNew(BookingId(1), FacilityId(1), CustomerId(1), BookingTimeInterval(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)), Instant.EPOCH)
        val incident = (Incident.createNew(IncidentId(1), FacilityId(1), EquipmentId(1), MeasurementId(1), IncidentType.HIGH_TEMPERATURE, IncidentSeverity.HIGH, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 20.0, Instant.EPOCH) as IncidentCreationResult.Success<Incident>).value
    }
}
