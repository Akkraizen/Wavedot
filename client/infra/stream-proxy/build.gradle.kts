plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.playbackApi)
        }
        getByName("jvmSharedMain").dependencies {
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}
