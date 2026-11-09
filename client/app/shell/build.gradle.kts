plugins {
    id("wavedot.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.client.core.data)
            api(projects.client.infra.database)
            api(projects.client.infra.hubClient)
            api(projects.client.infra.updater)
            api(projects.client.sources.local)
            api(projects.client.plugins.yandexMusic)
            api(projects.client.plugins.lrclib)
            api(projects.client.plugins.audius)
            api(projects.client.plugins.radioBrowser)
            api(projects.client.plugins.subsonic)
            api(projects.client.feature.player)
            api(projects.client.feature.library)
            api(projects.client.feature.search)
            api(projects.client.feature.settings)
            implementation(projects.client.ui.designsystem)
            implementation(libs.coil.network.ktor3)
            api(libs.koin.core)
            api(libs.koin.compose)
            api(libs.koin.compose.viewmodel)
        }
        jvmTest.dependencies {
            implementation(projects.client.core.testing)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
