plugins {
    id("wavedot.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.domain)
            api(libs.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(projects.client.core.testing)
        }
    }
}
