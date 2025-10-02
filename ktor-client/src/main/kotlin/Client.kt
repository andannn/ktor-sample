import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

suspend fun main() {
//    logging()
//    memoryCache()
//    responseContentNegotiation()
    requestBodyContentNegotiation()
}

private fun HttpClientConfig<*>.loggingConfig() {
    install(Logging) {
        logger =
            object : Logger {
                override fun log(message: String) {
                    println("Ktor Client: $message")
                }
            }
        level = LogLevel.HEADERS
    }
}

private suspend fun logging() {
    val client =
        HttpClient(OkHttp) {
            loggingConfig()
        }
    client.get("http://localhost:8082/").body<String>()
        .also {
            println("Response: $it")
        }
    client.close()
}

suspend fun memoryCache() {
    HttpClient(OkHttp) {
        loggingConfig()

        install(HttpCache)
    }.use { client ->
        client.get("http://localhost:8082/").body<String>()
            .also {
                println("Response$1:   $it")
            }
        client.get("http://localhost:8082/").body<String>()
            .also {
                println("Response$2:   $it")
            }
    }
}

suspend fun responseContentNegotiation() {
    @Serializable
    data class CustomResponse(
        val message: String,
        val code: Int,
    )

    HttpClient(OkHttp) {
        loggingConfig()

        install(ContentNegotiation) {
            json()
        }
    }.use { client ->
        client.get("http://localhost:8082/content_negotiation").body<CustomResponse>()
            .also {
                println("Response$1:   $it")
            }
    }
}

suspend fun requestBodyContentNegotiation() {
    @Serializable
    data class Request(val foo: String)

    HttpClient(OkHttp) {
        loggingConfig()

        install(ContentNegotiation) {
            json()
        }
    }.use { client ->
        client.post {
            url("http://localhost:8082/content_negotiation/withbody")
            header(HttpHeaders.ContentType, "application/json")
            setBody(Request("bar"))
        }
            .body<String>()
            .also {
                println("Response$1:   $it")
            }
    }
}
