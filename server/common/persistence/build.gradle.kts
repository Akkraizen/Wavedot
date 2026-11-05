plugins {
    id("wavedot.jvm.library")
}

dependencies {
    api(projects.server.common.kernel)
    api(projects.server.common.config)
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.hikari)
    api(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
}
