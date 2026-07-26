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
