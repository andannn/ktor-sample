plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.body.limit)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.data.conversion)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.freemarker)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.http.redirect)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.i18n)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.caching.headers)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
