plugins {
    id("wavedot.android.library")
}

dependencies {
    api(projects.client.core.playbackApi)
    api(libs.media3.exoplayer)
    api(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.kotlinx.coroutines.android)
}
