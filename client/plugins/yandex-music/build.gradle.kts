plugins {
    id("wavedot.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.pluginApi)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.client.core.testing)
            implementation(libs.ktor.client.mock)
        }
    }
}
