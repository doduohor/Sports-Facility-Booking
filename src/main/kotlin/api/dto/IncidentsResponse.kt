package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class IncidentsResponse(val items: List<IncidentResponse>)
