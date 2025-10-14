package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import freemarker.cache.ClassTemplateLoader
import io.ktor.callid.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.i18n.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.digest
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
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
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.server.util.*
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.html.*
import kotlinx.serialization.Serializable
import org.slf4j.event.Level
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.LocalDate
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object JWTConfig {
    val secret = "secret"
    val issuer = "http://0.0.0.0:8082/"
    val audience = "http://0.0.0.0:8080/hello"
    val myRealm = "Access to 'hello'"
}


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
                ContentType.Text.Plain -> CachingOptions(
                    CacheControl.MaxAge(
                        maxAgeSeconds = 3600
                    )
                )

                ContentType.Text.Html -> CachingOptions(
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
                limit = 200,
                refillPeriod = 2000.seconds
            )
        }
//        register(name = RateLimitName("CustomLimit")) {
//
//        }
    }

    install(Authentication) {
        basic("auth-basic") {
            realm = "Access to the '/' path"
            validate { credentials ->
                if (credentials.name == "jetbrains" && credentials.password == "foobar") {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }


        digest("auth-digest") {

            fun getMd5Digest(str: String): ByteArray = MessageDigest.getInstance("MD5").digest(str.toByteArray(UTF_8))

            val myRealm = "Access to the '/' path"
            val userTable: Map<String, ByteArray> = mapOf(
                "jetbrains" to getMd5Digest("jetbrains:$myRealm:foobar"),
                "admin" to getMd5Digest("admin:$myRealm:password")
            )

            realm = myRealm
            digestProvider { userName, realm ->
                userTable[userName]
            }
            validate { credentials ->
                if (credentials.userName.isNotEmpty()) {
                    CustomPrincipal(credentials.userName, credentials.realm)
                } else {
                    null
                }
            }
        }

        jwt("auth-jwt") {
            realm = JWTConfig.myRealm
            verifier(
                JWT
                .require(Algorithm.HMAC256(JWTConfig.secret))
                .withAudience(JWTConfig.audience)
                .withIssuer(JWTConfig.issuer)
                .build())
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
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

    install(SSE)

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
                call.respond(
                    CustomResponse(
                        message = "ok",
                        code = 200
                    )
                )
            }

            post("/withbody") {
                @Serializable
                data class Request(val foo: String)

                log.info("content_negotiation/withbody called")
                val req = call.receive<Request>()
                log.info("content_negotiation/withbody called ${req.foo}")
                call.respond(
                    "receive: ${req.foo}"
                )
            }
        }

        route("error_code") {
            get {
                call.respond(HttpStatusCode.BadRequest, "custom_error")
            }
        }

        route("/sse")  {
            sse {
                repeat(6) {
                    send(ServerSentEvent("this is SSE #$it"))
                    log.info("sse send #$it")
                    delay(1000)
                }
            }
        }

        route("basic_auth") {
            authenticate("auth-basic") {
                get {
                    call.respondText("Hello, ${call.principal<UserIdPrincipal>()?.name}!")
                }
            }
        }

        route("digest_auth") {
            authenticate("auth-digest") {
                get {
                    call.respondText("Hello, ${call.principal<CustomPrincipal>()?.userName}!")
                }
            }
        }

        route("jwt_auth") {
            install(ContentNegotiation) {
                json()
            }

            post("/login") {
                val user = call.receive<User>()
                // Check username and password
                // ...
                val token = JWT.create()
                    .withAudience(JWTConfig.audience)
                    .withIssuer(JWTConfig.issuer)
                    .withClaim("username", user.username)
                    .withExpiresAt(Date(System.currentTimeMillis() + 60000))
                    .sign(Algorithm.HMAC256(JWTConfig.secret))
                call.respond(hashMapOf("token" to token))
            }

            authenticate("auth-jwt") {
                get("/hello") {
                    val principal = call.principal<JWTPrincipal>()
                    val username = principal!!.payload.getClaim("username").asString()
                    val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
                    call.respondText("Hello, $username! Token is expired at $expiresAt ms.")
                }
            }
        }
    }
}

@Serializable
data class CustomResponse(
    val message: String,
    val code: Int,
)

class CustomException : Throwable()

data class CustomPrincipal(val userName: String, val realm: String)

@Serializable
data class User(val username: String, val password: String)
