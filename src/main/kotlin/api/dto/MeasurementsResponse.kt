package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeasurementsResponse(val items: List<MeasurementResponse>)
