plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        getByName("jvmSharedMain").dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}
