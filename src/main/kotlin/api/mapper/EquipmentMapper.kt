package com.doduohor.api.mapper

import com.doduohor.api.dto.EquipmentResponse
import com.doduohor.domain.model.Equipment

fun Equipment.toResponse() : EquipmentResponse =
    EquipmentResponse(
        id = id,
        facilityId = facilityId,
        name = name,
        type = type.toString().lowercase(),
        status = status.toString().lowercase()
    )