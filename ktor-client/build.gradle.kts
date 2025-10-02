plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.serialization)
    application
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "com.example.ClientKt"
}

dependencies {
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
}
