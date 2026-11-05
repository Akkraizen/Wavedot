plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.domain)
            api(projects.client.core.pluginApi)
        }
        commonTest.dependencies {
            implementation(projects.client.core.testing)
            implementation(libs.ktor.client.mock)
        }
    }
}
