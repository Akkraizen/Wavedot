plugins {
    id("wavedot.server.service")
}

application {
    mainClass.set("space.wavedot.server.updates.app.UpdatesApplicationKt")
    applicationName = "wavedot-updates"
}

dependencies {
    implementation(projects.server.updates.api)
    implementation(projects.server.updates.data)
    implementation(projects.server.common.config)
    implementation(libs.ktor.server.netty)
    implementation(libs.koin.ktor)
}
