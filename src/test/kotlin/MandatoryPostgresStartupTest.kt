package com.doduohor

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class MandatoryPostgresStartupTest {
    @Test
    fun `application fails before external connections when postgres is disabled`() {
        val failure = assertFailsWith<Throwable> {
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "database.enabled" to "false"
                    )
                }
                application {
                    module()
                }
            }
        }

        assertContains(failure.message.orEmpty(), "database.enabled")
    }
}
