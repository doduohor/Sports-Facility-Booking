package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FacilityCreate(val name: String, val type: String)
