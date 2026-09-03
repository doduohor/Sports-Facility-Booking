package com.doduohor.api.mapper

import com.doduohor.api.dto.ErrorResponse
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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

data class ApiError(val status: HttpStatusCode, val response: ErrorResponse)

suspend fun ApplicationCall.respondApiError(error: ApiError) {
    respond(error.status, error.response)
}

object ApiErrorMapper {
    fun map(result: CreateFacilityResult): ApiError? = when (result) {
        CreateFacilityResult.InvalidName -> error(400, "invalidName", "Facility name must not be blank")
        is CreateFacilityResult.Success -> null
    }

    fun map(result: ActivateFacilityResult): ApiError? = when (result) {
        ActivateFacilityResult.InvalidStatus -> error(409, "invalidStatus", "The object cannot be activated from its current status")
        ActivateFacilityResult.AlreadyActive -> error(409, "alreadyActive", "The object is already active")
        ActivateFacilityResult.NotFound -> error(404, "Error", "Not Found")
        is ActivateFacilityResult.Success -> null
    }

    fun map(result: CreateBookingResult): ApiError? = when (result) {
        CreateBookingResult.InvalidFacilityId -> error(400, "invalidFacilityId", "The Facility ID must be positive.")
        CreateBookingResult.InvalidCustomerId -> error(400, "invalidCustomerId", "Customer ID is not allowed to create bookings.")
        CreateBookingResult.InvalidTimeInterval -> error(400, "invalidTimeInterval", "The time interval must be between 1 and 12 hours.")
        CreateBookingResult.NotFindFacilityId -> error(404, "notFindFacilityId", "The specified facilityId was not found.")
        CreateBookingResult.InvalidStatusFacilityId -> error(409, "invalidStatusFacilityId", "The status of this facility does not allow bookings to be created.")
        CreateBookingResult.UnavailableRangeTimeLimit -> error(409, "unavailableRangeTimeLimit", "The specified time slot is partially or fully booked.")
        is CreateBookingResult.Success -> null
    }

    fun map(result: FindByFacilityResult): ApiError? = when (result) {
        FindByFacilityResult.InvalidFacilityId -> error(400, "invalidFacilityId", "You entered an incorrect facilityId")
        FindByFacilityResult.NotFindFacilityId -> error(404, "notFindFacilityId", "The specified Facility ID does not exist")
        is FindByFacilityResult.Success -> null
    }

    fun map(result: CreateEquipmentResult): ApiError? = when (result) {
        CreateEquipmentResult.InvalidFacilityId -> error(400, "invalidFacilityId", "You entered an incorrect facilityId")
        CreateEquipmentResult.NotFindFacilityId -> error(404, "notFindFacilityId", "The specified Facility ID does not exist")
        CreateEquipmentResult.InvalidName -> error(400, "invalidName", "An incorrect name has been specified")
        is CreateEquipmentResult.Success -> null
    }

    fun map(result: FindEquipmentsByFacilityIdResult): ApiError? = when (result) {
        FindEquipmentsByFacilityIdResult.InvalidFacilityId -> error(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
        FindEquipmentsByFacilityIdResult.NotFindFacilityId -> error(404, "notFindFacilityId", "The specified Facility ID does not exist")
        is FindEquipmentsByFacilityIdResult.Success -> null
    }

    fun map(result: CreateMeasurementResult): ApiError? = when (result) {
        CreateMeasurementResult.InvalidEquipmentId -> error(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
        CreateMeasurementResult.NotFindEquipmentId -> error(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
        CreateMeasurementResult.InvalidMappingTypeAndUnit -> error(400, "invalidMappingTypeAndUnit", "The measurement type does not match the specified unit")
        CreateMeasurementResult.NotSupportedEquipmentType -> error(500, "notSupportedEquipmentType", "Measurement rules are not configured for this equipment type")
        CreateMeasurementResult.InvalidMeasurementType -> error(409, "invalidMeasurementType", "This measurement type is not supported by the specified equipment")
        CreateMeasurementResult.InvalidValue -> error(400, "invalidValue", "The measurement value is outside the allowed range")
        CreateMeasurementResult.MeasurementRangeNotConfigured -> error(500, "measurementRangeNotConfigured", "Measurement value range is not configured")
        CreateMeasurementResult.NotFindMeasurementType -> error(404, "notFindMeasurementType", "The specified Measurement Type does not exist")
        is CreateMeasurementResult.Success -> null
    }

    fun map(result: FindEquipmentIdResult): ApiError? = when (result) {
        FindEquipmentIdResult.InvalidEquipmentId -> error(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
        FindEquipmentIdResult.NotFindEquipmentId -> error(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
        is FindEquipmentIdResult.Success -> null
    }

    fun map(result: IncidentServiceResult): ApiError? = when (result) {
        IncidentServiceResult.InvalidFacilityId -> error(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
        IncidentServiceResult.InvalidEquipmentId -> error(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
        IncidentServiceResult.InvalidMeasurementId -> error(400, "invalidMeasurementId", "An incorrect Measurement ID has been specified")
        IncidentServiceResult.NotFindFacilityId -> error(404, "notFindFacilityId", "The specified Facility ID does not exist")
        IncidentServiceResult.NotFindEquipmentId -> error(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
        IncidentServiceResult.EquipmentDoesNotBelongToFacility -> error(409, "equipmentDoesNotBelongToFacility", "The equipment does not belong to the specified facility")
        IncidentServiceResult.InvalidValue -> error(400, "invalidValue", "An incorrect incident value has been specified")
        is IncidentServiceResult.Success -> null
    }

    fun map(result: FindIncidentsByFacilityIdResult): ApiError? = when (result) {
        FindIncidentsByFacilityIdResult.InvalidFacilityId -> error(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
        FindIncidentsByFacilityIdResult.NotFindFacilityId -> error(404, "notFindFacilityId", "The specified Facility ID does not exist")
        is FindIncidentsByFacilityIdResult.Success -> null
    }

    fun map(result: FindIncidentsByEquipmentIdResult): ApiError? = when (result) {
        FindIncidentsByEquipmentIdResult.InvalidEquipmentId -> error(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
        FindIncidentsByEquipmentIdResult.NotFindEquipmentId -> error(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
        is FindIncidentsByEquipmentIdResult.Success -> null
    }

    fun map(result: IncidentLifecycleServiceResult): ApiError? = when (result) {
        IncidentLifecycleServiceResult.InvalidIncidentId -> error(400, "invalidIncidentId", "An incorrect Incident ID has been specified")
        IncidentLifecycleServiceResult.InvalidStatus -> error(409, "invalidStatus", "The incident cannot be transitioned from its current status")
        IncidentLifecycleServiceResult.AlreadyInProgress -> error(409, "alreadyInProgress", "The incident is already in progress")
        IncidentLifecycleServiceResult.AlreadyInFalsePositive -> error(409, "alreadyInFalsePositive", "The incident is already marked as false positive")
        IncidentLifecycleServiceResult.AlreadyResolved -> error(409, "alreadyResolved", "The incident is already resolved")
        IncidentLifecycleServiceResult.AlreadyClosed -> error(409, "alreadyClosed", "The incident is already closed")
        IncidentLifecycleServiceResult.AlreadyReopen -> error(409, "alreadyReopen", "The incident is already reopened")
        IncidentLifecycleServiceResult.StatusUpdateError -> error(500, "statusUpdateError", "The incident status could not be updated")
        IncidentLifecycleServiceResult.NotFindIncidentId -> error(404, "notFindIncidentId", "The specified Incident ID does not exist")
        is IncidentLifecycleServiceResult.Success -> null
    }

    fun map(result: MonitoringServiceResult): ApiError? = when (result) {
        is MonitoringServiceResult.MeasurementCreateError -> map(result.measurementResult)
        is MonitoringServiceResult.EquipmentContextLost -> error(500, "equipmentContextLost", "Equipment context was not found after measurement creation")
        is MonitoringServiceResult.IncidentCreateError -> error(500, "incidentCreateError", "Measurement was created, but incident creation failed")
        is MonitoringServiceResult.OutboxPersistenceError -> error(500, "outboxPersistenceError", "Measurement event could not be stored")
        is MonitoringServiceResult.SuccessWithIncident, is MonitoringServiceResult.SuccessWithoutIncident -> null
    }

    private fun error(code: Int, name: String, text: String): ApiError =
        ApiError(HttpStatusCode.fromValue(code), ErrorResponse(code, name, text))
}
