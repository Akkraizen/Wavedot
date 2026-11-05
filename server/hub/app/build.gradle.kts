plugins {
    id("wavedot.server.service")
}

application {
    mainClass.set("space.wavedot.server.hub.app.HubApplicationKt")
    applicationName = "wavedot-hub"
}

dependencies {
    implementation(projects.server.hub.api)
    implementation(projects.server.hub.data)
    implementation(projects.server.common.config)
    implementation(libs.ktor.server.netty)
    implementation(libs.koin.ktor)
}
