package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: Int, val name: String = "Error", val text: String = "Retry call")