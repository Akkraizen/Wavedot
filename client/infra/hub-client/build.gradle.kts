plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.domain)
            api(projects.shared.protocolHub)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(projects.client.core.testing)
            implementation(libs.ktor.client.mock)
        }
    }
}
