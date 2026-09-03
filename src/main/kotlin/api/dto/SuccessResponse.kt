package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SuccessResponse(val name: String = "Error", val text: String = "Retry call")