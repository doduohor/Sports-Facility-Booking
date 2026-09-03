package com.doduohor.api.mapper

import com.doduohor.api.dto.EquipmentResponse
import com.doduohor.domain.model.Equipment

fun Equipment.toResponse() : EquipmentResponse =
    EquipmentResponse(
        id = id.value,
        facilityId = facilityId.value,
        name = name,
        type = type.toString().lowercase(),
        status = status.toString().lowercase()
    )
