plugins {
    id("wavedot.android.application")
}

android {
    namespace = "space.wavedot.app.android"
    defaultConfig {
        applicationId = "space.wavedot"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    packaging {
        resources {
            excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties", "META-INF/DEPENDENCIES")
        }
    }
}

dependencies {
    implementation(projects.client.app.shell)
    implementation(projects.client.infra.network)
    implementation(projects.client.infra.storage)
    implementation(projects.client.infra.streamProxy)
    implementation(projects.client.infra.playbackAndroid)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
}
