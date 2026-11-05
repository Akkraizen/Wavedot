plugins {
    id("wavedot.jvm.library")
}

dependencies {
    api(projects.server.hub.domain)
    api(projects.server.common.ktor)
    api(projects.shared.protocolHub)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.json)
    testImplementation(projects.server.common.testing)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
}

dependencies {
    testImplementation(projects.server.hub.data)
}
