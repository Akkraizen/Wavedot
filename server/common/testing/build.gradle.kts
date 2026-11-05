plugins {
    id("wavedot.jvm.library")
}

dependencies {
    api(projects.server.common.persistence)
    api(libs.h2)
    api(libs.ktor.server.test.host)
    api("org.jetbrains.kotlin:kotlin-test")
    api(libs.kotlinx.coroutines.test)
}
