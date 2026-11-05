plugins {
    id("wavedot.jvm.library")
}

val nativePlatforms = listOf("windows-x86_64", "linux-x86_64", "macosx-arm64", "macosx-x86_64")

dependencies {
    api(projects.client.core.playbackApi)
    implementation(projects.client.core.common)
    implementation(libs.bytedeco.ffmpeg)
    implementation(libs.bytedeco.javacpp)
    nativePlatforms.forEach { platform ->
        runtimeOnly(variantOf(libs.bytedeco.ffmpeg) { classifier(platform) })
        runtimeOnly(variantOf(libs.bytedeco.javacpp) { classifier(platform) })
    }
}

dependencies {
    testImplementation(projects.client.infra.streamProxy)
}
