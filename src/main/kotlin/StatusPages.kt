package com.doduohor

import com.doduohor.api.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.format.DateTimeParseException

private val apiErrorJson = Json { encodeDefaults = true }

private suspend fun ApplicationCall.respondApiError(status: HttpStatusCode, name: String, text: String) {
    respondText(
        text = apiErrorJson.encodeToString(ErrorResponse(status.value, name, text)),
        contentType = ContentType.Application.Json,
        status = status
    )
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respondApiError(HttpStatusCode.BadRequest, "invalidRequest", "The request body is invalid")
        }

        exception<ContentTransformationException> { call, _ ->
            call.respondApiError(HttpStatusCode.BadRequest, "invalidRequest", "The request body is invalid")
        }

        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respondApiError(HttpStatusCode.UnsupportedMediaType, "unsupportedContentType", "The Content-Type header is not supported")
        }

        exception<DateTimeParseException> { call, _ ->
            call.respondApiError(HttpStatusCode.BadRequest, "invalidRequest", "The request contains an invalid date or time")
        }

        status(HttpStatusCode.NotAcceptable) { call, _ ->
            call.respondApiError(HttpStatusCode.NotAcceptable, "unsupportedAccept", "The Accept header is not supported")
        }

        exception<Throwable> { call, _ ->
            call.respondApiError(
                HttpStatusCode.InternalServerError,
                "internalServerError",
                "An unexpected error occurred"
            )
        }
    }
}
