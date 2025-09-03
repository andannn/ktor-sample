package com.example

import io.ktor.callid.KtorCallIdContextElement
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.event.Level

fun Application.configureRouting() {
    install(CallLogging) {
        level = Level.INFO

        callIdMdc("call-id")
    }

// TODO: setup CallId
//  generate callid for each  request.
    install(CallId) {
        generate(10)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }

    routing {
        get("/") {
            log.info("/ called")
            log.info("/ called KtorCallIdContextElement  ${call.coroutineContext[KtorCallIdContextElement]?.callId}")
            call.respondText("Hello, World!")
        }
        get("/error") {
// TODO: setupFallbackResponse
//  ktor/ktor-server/ktor-server-core/common/src/io/ktor/server/engine/BaseApplicationResponse.kt
//  respond 500 when an exception occurs
            error("fail")
        }
    }
}
