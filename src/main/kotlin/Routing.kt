package com.example

import freemarker.cache.ClassTemplateLoader
import io.ktor.callid.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.i18n.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.html.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.dataconversion.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.html.*
import org.slf4j.event.Level
import java.time.LocalDate
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    install(RateLimit) {
        global {
            rateLimiter(
                limit = 2,
                refillPeriod = 15.seconds
            )
        }
//        register(name = RateLimitName("CustomLimit")) {
//
//        }
    }

//    install(DataConversion) {
//    }

//    install(I18n) {
//        availableLanguages = listOf("en-US", "pt-BR")
//        defaultLanguage = "en-US"
//    }

    install(StatusPages) {
        exception<CustomException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                mapOf("message" to "validation failed")
            )
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

        route("/limit") {
            install(RequestBodyLimit) {
                bodyLimit {
                    5
                }
            }
            get("max") {
                call.respond("max limitation 5 byte")
            }
        }

        route("/head") {
            install(AutoHeadResponse)

            get {
                call.respond("auto head response")
            }
        }

        route("/condition") {
            install(ConditionalHeaders) {
                version { call, outgoingContent ->
                    listOf(
                        EntityTagVersion("abc123"),
                        LastModifiedVersion(Date(1646387527500))
                    )
                }
            }

            get {
                call.respondText("Hello, world!")
            }
        }

        route("/compression") {
            install(Compression) {
                gzip {
                    priority = 0.9
                }
                deflate()
            }

            get {
                call.respond(
                    buildString {
                        repeat(100) {
                            append("AAA")
                        }
                    }
                )
            }
        }

        route("/param") {
            get {
                val id: Int by call.request.queryParameters
                call.respond("id passed: $id")
            }
        }

        route("default_header") {
            install(DefaultHeaders)

            get {
                call.respond("Default Header result")
            }
        }

        route("freemarker") {
            install(FreeMarker) {
                templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
            }
            get {
                call.respond(
                    FreeMarkerContent(
                        "hello.ftl",
                        mapOf(
                            "user" to "ABVVC",
                            "date" to LocalDate.now()
                        )
                    )
                )
            }
        }

        route("html_builder") {
            get {
                call.respondHtml {
                    body {
                        div {
                            a("https://kotlinlang.org") {
                                target = ATarget.blank
                                +"Main site"
                            }
                        }
                    }
                }
            }
        }

        route("partial") {
            install(PartialContent)

            get {
                val channel = ByteChannel()
                launch {
                    delay(2000)
                    channel.writeStringUtf8("12345123451234512345")
                    channel.close()
                }

                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override fun readFrom(): ByteReadChannel = channel
                        override val contentLength: Long = 20
                        override val contentType: ContentType = ContentType.Text.Plain
                    }
                )
            }
        }

        route("limit") {
            get {
                call.respond("limit")
            }
        }

        route("reqValidation") {
            install(RequestValidation) {
                validate<String> {
                    ValidationResult.Valid
                }
            }

            get {
                call.respond("result")
            }
        }

        route("custom_exception") {

            get {
                throw CustomException()
            }
        }

        route("content_negotiation") {
            install(ContentNegotiation) {
                json()
            }

            get {
                call.respond(mapOf("hello" to "world"))
            }
        }
    }
}


class CustomException : Throwable()