plugins {
    id("wavedot.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.domain)
            implementation(projects.client.ui.designsystem)
            api(libs.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(projects.client.core.testing)
        }
    }
}
