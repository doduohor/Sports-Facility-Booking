package com.doduohor.domain.model

enum class FacilityType {
    GYM,
    POOL,
    STADIUM
}

enum class FacilityStatus {
    ACTIVE,
    INACTIVE,
    MAINTENANCE
}

data class Facility(
    val id: Long,
    val name: String,
    val type: FacilityType,
    val status: FacilityStatus
) {
    companion object {
        fun createNew(id: Long, name: String, type: FacilityType): Facility {
            require(name.isNotBlank()) { "Facility name must not be blank" }

            return Facility(
                id = id,
                name = name.trim(),
                type = type,
                status = FacilityStatus.INACTIVE
            )
        }
    }
}

fun Facility.activate(): FacilityActivateResult {
    return when (status) {
        FacilityStatus.INACTIVE -> FacilityActivateResult.Success(copy(status = FacilityStatus.ACTIVE))
        FacilityStatus.MAINTENANCE -> FacilityActivateResult.InvalidStatus
        FacilityStatus.ACTIVE -> FacilityActivateResult.AlreadyActive
    }
}

fun Facility.canBeBooked() = status == FacilityStatus.ACTIVE

sealed interface FacilityActivateResult {
    data class Success(val facility: Facility) : FacilityActivateResult
    data object InvalidStatus : FacilityActivateResult
    data object AlreadyActive : FacilityActivateResult
}
