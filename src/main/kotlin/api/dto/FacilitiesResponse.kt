package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FacilitiesResponse(val items: List<FacilityResponse>)
