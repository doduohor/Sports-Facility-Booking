package com.doduohor

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.event.Level
import java.util.*

private val requestIdRegex = Regex("^[A-Za-z0-9._-]{1,128}$")

fun Application.configureLogging() {
    install(CallId) {
        header(HttpHeaders.XRequestId)

        generate {
            UUID.randomUUID().toString()
        }

        verify { callId ->
            requestIdRegex.matches(callId)
        }
    }

    install(CallLogging) {
        level = Level.INFO

        filter { call ->
            call.request.path().startsWith("/api/")
        }

        format { call ->
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val processingTimeMs = call.processingTimeMillis().toString()
            val status = call.response.status()
            "$method $path -> $status, $processingTimeMs ms"
        }

        callIdMdc("requestId")
    }

}