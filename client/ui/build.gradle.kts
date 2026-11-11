plugins {
    id("wavedot.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.model)
            api(projects.client.presentation.player)
            api(projects.client.presentation.library)
            api(projects.client.presentation.search)
            api(projects.client.presentation.settings)
            api(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            api(libs.lifecycle.viewmodel.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}
