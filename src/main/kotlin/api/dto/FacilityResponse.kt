package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FacilityResponse(val id: Long, val name: String, val type: String, val status: String)
