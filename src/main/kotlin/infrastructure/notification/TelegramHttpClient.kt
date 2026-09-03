package com.doduohor.infrastructure.notification

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

interface TelegramHttpClient {
    fun postForm(url: String, form: Map<String, String>): TelegramHttpResponse
}

data class TelegramHttpResponse(
    val statusCode: Int,
    val body: String
)

class JavaTelegramHttpClient : TelegramHttpClient {
    private val client = HttpClient.newHttpClient()

    override fun postForm(url: String, form: Map<String, String>): TelegramHttpResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return TelegramHttpResponse(
            statusCode = response.statusCode(),
            body = response.body()
        )
    }

    private fun formEncode(values: Map<String, String>): String =
        values.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
