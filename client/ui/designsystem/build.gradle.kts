plugins {
    id("wavedot.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.model)
        }
    }
}
