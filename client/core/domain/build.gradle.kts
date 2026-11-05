plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.contract)
            api(projects.client.core.playbackApi)
        }
        commonTest.dependencies {
            implementation(projects.client.core.testing)
        }
    }
}
