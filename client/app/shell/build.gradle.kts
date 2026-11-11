plugins {
    id("wavedot.kmp.library")
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
            api(projects.client.presentation.player)
            api(projects.client.presentation.library)
            api(projects.client.presentation.search)
            api(projects.client.presentation.settings)
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        jvmTest.dependencies {
            implementation(projects.client.core.testing)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
