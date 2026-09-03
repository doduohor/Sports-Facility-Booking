package com.doduohor.domain.model

import com.doduohor.domain.shared.FacilityId

enum class FacilityType {
    GYM,
    POOL,
    STADIUM;

    companion object{
        fun fromString(value: String): FacilityType? =
            entries.firstOrNull { it.name.equals(value.trim(), true) }
    }
}

enum class FacilityStatus {
    ACTIVE,
    INACTIVE,
    MAINTENANCE
}

data class Facility(
    val id: FacilityId,
    val name: String,
    val type: FacilityType,
    val status: FacilityStatus
) {
    fun canAcceptBooking(): Boolean = this.status == FacilityStatus.ACTIVE
    fun activate(): FacilityActivateResult {
        return when (status) {
            FacilityStatus.INACTIVE -> FacilityActivateResult.Success(copy(status = FacilityStatus.ACTIVE))
            FacilityStatus.MAINTENANCE -> FacilityActivateResult.InvalidStatus
            FacilityStatus.ACTIVE -> FacilityActivateResult.AlreadyActive
        }
    }

    companion object {
        val DEFAULT_STATUS = FacilityStatus.INACTIVE
        fun createNew(id: FacilityId, name: String, type: FacilityType): FacilityCreationResult<Facility> {
            return if(name.isBlank())
                FacilityCreationResult.InvalidName
            else
                FacilityCreationResult.Success(
                    Facility(
                        id = id,
                        name = name.trim(),
                        type = type,
                        status = DEFAULT_STATUS
                    )
                )
        }
    }
}

sealed interface FacilityActivateResult {
    data class Success(val facility: Facility) : FacilityActivateResult
    data object InvalidStatus : FacilityActivateResult
    data object AlreadyActive : FacilityActivateResult
}

sealed interface FacilityCreationResult<out T>{
    data class Success<T>(val value: T): FacilityCreationResult<T>
    data object InvalidName: FacilityCreationResult<Nothing>
}
