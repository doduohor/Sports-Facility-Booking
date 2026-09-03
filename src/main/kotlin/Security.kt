package com.doduohor

import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic

fun Application.configureSecurity(){
    val username = environment.config.property("security.basic.username").getString()
    val password = environment.config.property("security.basic.password").getString()
    authentication {
        basic("auth-basic") {
            realm = "Access to Api"
            validate { credentials ->
                if (
                    credentials.name == username &&
                    credentials.password == password)
                {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}