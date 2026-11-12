plugins {
    id("wavedot.jvm.library")
}

dependencies {
    api(projects.server.common.kernel)
    api(projects.shared.protocolCore)
    api(libs.ktor.server.core)
    api(libs.ktor.server.auth)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    api(libs.ktor.server.rate.limit)
    api(libs.ktor.server.body.limit)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.serialization.json)
    api(libs.logback)
}
