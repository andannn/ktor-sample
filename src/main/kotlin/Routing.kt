package com.example

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        get("/error") {
            // ktor/ktor-server/ktor-server-core/common/src/io/ktor/server/engine/BaseApplicationResponse.kt
            // setupFallbackResponse
            // respond 500 when an exception occurs
            error("fail")
        }
    }
}
