package com.doduohor

import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readLine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductionModuleTest {

    @Test
    fun `production module starts and serves endpoints through koin`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "security.basic.username" to "admin",
                "security.basic.password" to "admin",
                "database.enabled" to "false"
            )
        }
        application {
            module()
        }

        val healthResponse = client.get("/health")
        assertEquals(HttpStatusCode.OK, healthResponse.status)

        val unauthorizedCreateResponse = client.post("/api/facilities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Central Pool",
                  "type": "POOL"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthorizedCreateResponse.status)

        val createResponse = client.post("/api/facilities") {
            basicAuth("admin", "admin")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Central Pool",
                  "type": "POOL"
                }
                """.trimIndent()
            )
        }
        val createBody = createResponse.bodyAsText()

        assertEquals(HttpStatusCode.Created, createResponse.status)
        assertTrue(createBody.contains("Central Pool"))

        val getResponse = client.get("/api/facilities/1")
        val getBody = getResponse.bodyAsText()

        assertEquals(HttpStatusCode.OK, getResponse.status)
        assertTrue(getBody.contains("Central Pool"))
    }

    @Test
    fun `production module serves connected sse event`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "security.basic.username" to "admin",
                "security.basic.password" to "admin",
                "database.enabled" to "false"
            )
        }
        application {
            module()
        }

        client.prepareGet("/api/events/stream").execute { response ->
            val bodyChannel = response.bodyAsChannel()
            val receivedLines = mutableListOf<String>()

            withTimeout(1_000) {
                while (receivedLines.size < 2) {
                    val line = bodyChannel.readLine() ?: break
                    if (line.isNotBlank()) {
                        receivedLines.add(line)
                    }
                }
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.contains("text/event-stream") == true)
            assertTrue(receivedLines.contains("event: connected"))
            assertTrue(receivedLines.contains("""data: {"message":"event stream connected"}"""))
        }
    }

    @Test
    fun `sse stream receives measurement and incident events`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "security.basic.username" to "admin",
                "security.basic.password" to "admin",
                "database.enabled" to "false"
            )
        }
        application {
            module()
        }

        val facilityResponse = client.post("/api/facilities") {
            basicAuth("admin", "admin")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Central Pool",
                  "type": "POOL"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, facilityResponse.status)
        val facilityId = extractLongField(facilityResponse.bodyAsText(), "id")

        val equipmentResponse = client.post("/api/equipments") {
            basicAuth("admin", "admin")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "facilityId": $facilityId,
                  "name": "Fire alarm",
                  "type": "FIRE_ALARM"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, equipmentResponse.status)
        val equipmentId = extractLongField(equipmentResponse.bodyAsText(), "id")

        client.prepareGet("/api/events/stream").execute { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val bodyChannel = response.bodyAsChannel()

            coroutineScope {
                val connectedLines = mutableListOf<String>()
                withTimeout(1_000) {
                    while (connectedLines.size < 2) {
                        val line = bodyChannel.readLine() ?: break
                        if (line.isNotBlank()) {
                            connectedLines.add(line)
                        }
                    }
                }

                assertTrue(connectedLines.contains("event: connected"))

                val measurementJob = async {
                    client.post("/api/measurements") {
                        basicAuth("admin", "admin")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "equipmentId": $equipmentId,
                              "type": "SMOKE",
                              "unit": "PERCENT",
                              "value": 12.0
                            }
                            """.trimIndent()
                        )
                    }
                }

                val eventLines = mutableListOf<String>()
                withTimeout(1_000) {
                    while (eventLines.size < 4) {
                        val line = bodyChannel.readLine() ?: break
                        if (line.isNotBlank()) {
                            eventLines.add(line)
                        }
                    }
                }

                val measurementResponse = measurementJob.await()
                assertEquals(HttpStatusCode.Created, measurementResponse.status)
                assertEquals(
                    listOf("event: measurement_created", "event: incident_created"),
                    eventLines.filter { it.startsWith("event: ") }
                )
                assertTrue(eventLines.any { it.contains("\"equipmentId\":$equipmentId") })
                assertTrue(eventLines.any { it.contains("\"type\":\"smoke_detected\"") })
            }
        }
    }

    private fun extractLongField(body: String, fieldName: String): Long {
        val pattern = Regex("""\"$fieldName\"\s*:\s*(\d+)""")
        return pattern.find(body)?.groupValues?.get(1)?.toLong()
            ?: error("Response does not contain numeric field '$fieldName': $body")
    }
}
