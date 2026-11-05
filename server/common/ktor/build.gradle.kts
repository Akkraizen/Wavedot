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
    implementation(libs.ktor.serialization.json)
    api(libs.logback)
}
