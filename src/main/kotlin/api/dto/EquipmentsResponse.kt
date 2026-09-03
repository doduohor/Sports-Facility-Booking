package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class EquipmentsResponse(val items: List<EquipmentResponse>)
