plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.common)
        }
        jvmMain.dependencies {
            implementation(libs.jna.platform)
        }
    }
}
