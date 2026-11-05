plugins {
    id("wavedot.jvm.library")
}

dependencies {
    api(projects.server.updates.domain)
    api(projects.server.common.ktor)
    api(projects.shared.protocolUpdates)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.auto.head)
    testImplementation(projects.server.common.testing)
    testImplementation(projects.server.updates.data)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.json)
}
