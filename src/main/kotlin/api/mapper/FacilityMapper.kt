package com.doduohor.api.mapper

import com.doduohor.api.dto.FacilityResponse
import com.doduohor.domain.model.Facility

fun Facility.toResponse(): FacilityResponse =
    FacilityResponse(
        id = id,
        name = name,
        type = type.name.lowercase(),
        status = status.name.lowercase()
    )
