package com.doduohor

import com.doduohor.di.configureTestKoin
import com.doduohor.infrastructure.messaging.MessagePublisher
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticationContractTest {
    private companion object {
        const val USERNAME = "admin"
        const val PASSWORD = "admin"
        const val WRONG_PASSWORD = "wrong-password"
    }

    @Test
    fun `all protected operations reject missing credentials`() = testApplication {
        configureTestApplication()

        protectedRoutes.forEach { route ->
            assertEquals(HttpStatusCode.Unauthorized, request(route))
        }
    }

    @Test
    fun `all protected operations reject invalid credentials`() = testApplication {
        configureTestApplication()

        protectedRoutes.forEach { route ->
            assertEquals(HttpStatusCode.Unauthorized, request(route, USERNAME, WRONG_PASSWORD))
        }
    }

    @Test
    fun `valid credentials let protected operations reach business logic`() = testApplication {
        configureTestApplication()

        protectedRoutes.forEach { route ->
            assertEquals(route.expectedStatus, request(route, USERNAME, PASSWORD))
        }
    }

    @Test
    fun `public routes are available without credentials`() = testApplication {
        configureTestApplication()

        publicGetRoutes.forEach { route ->
            assertEquals(route.expectedStatus, client.get(route.path).status)
        }
    }

    private suspend fun ApplicationTestBuilder.request(
        route: ProtectedRoute,
        username: String? = null,
        password: String? = null
    ): HttpStatusCode = client.request(route.path) {
        method = route.method
        if (username != null && password != null) {
            basicAuth(username, password)
        }
        contentType(ContentType.Application.Json)
        setBody(route.body)
    }.status

    private fun ApplicationTestBuilder.configureTestApplication() {
        environment {
            config = MapApplicationConfig(
                "security.basic.username" to USERNAME,
                "security.basic.password" to PASSWORD,
                "database.enabled" to "false",
                "rabbitmq.host" to "localhost",
                "rabbitmq.port" to "5672",
                "rabbitmq.username" to "guest",
                "rabbitmq.password" to "guest",
                "rabbitmq.exchange" to "sports.events",
                "rabbitmq.queue" to "sports.measurements",
                "rabbitmq.routingKey" to "measurement.created",
                "rabbitmq.deadLetterExchange" to "sports.events.dlq",
                "rabbitmq.deadLetterQueue" to "sports.measurements.dlq",
                "rabbitmq.deadLetterRoutingKey" to "measurement.created.dlq"
            )
        }

        application {
            configureTestKoin(FakeMessagePublisher())
            configureSerialization()
            configureStatusPages()
            configureSecurity()
            configureSSE()
            configureRouting()
        }
    }

    private class FakeMessagePublisher : MessagePublisher {
        override suspend fun publish(message: String) = Unit
    }

    private data class ProtectedRoute(
        val method: HttpMethod,
        val path: String,
        val body: String,
        val expectedStatus: HttpStatusCode = HttpStatusCode.BadRequest
    )

    private data class PublicGetRoute(
        val path: String,
        val expectedStatus: HttpStatusCode
    )

    private val protectedRoutes = listOf(
        ProtectedRoute(HttpMethod.Post, "/api/facilities", """{"name":"","type":"POOL"}"""),
        ProtectedRoute(
            HttpMethod.Put,
            "/api/facilities/999999/activate",
            "",
            HttpStatusCode.NotFound
        ),
        ProtectedRoute(HttpMethod.Post, "/api/bookings", """{"facilityId":0,"customerId":900,"bookingDate":"2026-07-28","startTime":"10:00","endTime":"12:00"}"""),
        ProtectedRoute(HttpMethod.Post, "/api/equipments", """{"facilityId":0,"name":"Equipment","type":"VENTILATION"}"""),
        ProtectedRoute(HttpMethod.Post, "/api/measurements", """{"equipmentId":0,"type":"TEMPERATURE","unit":"CELSIUS","value":20.0}"""),
        ProtectedRoute(HttpMethod.Post, "/api/incidents", """{"facilityId":0,"equipmentId":1,"measurementId":1,"type":"SMOKE_DETECTED","severity":"HIGH","measurementType":"SMOKE","measurementUnit":"PERCENT","value":12.0}""")
    )

    private val publicGetRoutes = listOf(
        PublicGetRoute("/", HttpStatusCode.OK),
        PublicGetRoute("/json/kotlinx-serialization", HttpStatusCode.OK),
        PublicGetRoute("/health", HttpStatusCode.OK),
        PublicGetRoute("/api/facilities", HttpStatusCode.OK),
        PublicGetRoute("/api/facilities/1/readings", HttpStatusCode.OK),
        PublicGetRoute("/api/facilities/1", HttpStatusCode.NotFound),
        PublicGetRoute("/api/bookings", HttpStatusCode.OK),
        PublicGetRoute("/api/bookings/1", HttpStatusCode.NotFound),
        PublicGetRoute("/api/facilities/1/bookings", HttpStatusCode.NotFound),
        PublicGetRoute("/api/equipments", HttpStatusCode.OK),
        PublicGetRoute("/api/equipments/1", HttpStatusCode.NotFound),
        PublicGetRoute("/api/facilities/1/equipments", HttpStatusCode.NotFound),
        PublicGetRoute("/api/measurements", HttpStatusCode.OK),
        PublicGetRoute("/api/measurements/1", HttpStatusCode.NotFound),
        PublicGetRoute("/api/equipments/1/measurements", HttpStatusCode.NotFound),
        PublicGetRoute("/api/incidents", HttpStatusCode.OK),
        PublicGetRoute("/api/incidents/1", HttpStatusCode.NotFound),
        PublicGetRoute("/api/facilities/1/incidents", HttpStatusCode.NotFound),
        PublicGetRoute("/api/equipments/1/incidents", HttpStatusCode.NotFound)
    )
}
