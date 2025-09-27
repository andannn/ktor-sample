package com.example

import io.ktor.callid.KtorCallIdContextElement
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.*
import io.ktor.server.http.content.CachingOptions
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.read
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.event.Level

fun Application.configureRouting() {
    install(CallLogging) {
        level = Level.INFO

        callIdMdc("call-id")
    }

// TODO: Caching Headers
// https://ktor.io/docs/server-caching-headers.html#configure
//
    install(CachingHeaders) {
        options { call, content ->
            val type = content.contentType?.withoutParameters()
            when (type) {
                ContentType.Text.Plain -> io.ktor.http.content.CachingOptions(
                    CacheControl.MaxAge(
                        maxAgeSeconds = 3600
                    )
                )
                ContentType.Text.Html -> io.ktor.http.content.CachingOptions(
                    CacheControl.MaxAge(
                        maxAgeSeconds = 60
                    )
                )
                else -> null
            }
        }
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
        get("/download") {
            val channel = ByteChannel()
            val job = launch(Dispatchers.Default) {
                while (true) {
                    delay(1000)
                    log.info("writeStringUtf8 a")
                    channel.writeStringUtf8("a")
                    channel.flush()
                }
            }
            job.invokeOnCompletion {
                log.info("writeStringUtf8 completed")
            }
            call.respond(object : OutgoingContent.ReadChannelContent() {
                override fun readFrom(): ByteReadChannel = channel
                override val contentLength: Long? = "AAABBB".length.toLong()
                override val contentType: ContentType = ContentType.Text.Plain
            })
        }

        get("/byte_read_channel_test") {
            val channel = ByteChannel()

            launch {
                delay(2000)
                channel.writeStringUtf8("AAA")
                channel.close()
            }

            val content = channel.awaitContent(1)
            log.info("byte_read_channel_test $content")

            call.respond(content)
        }
    }
}
